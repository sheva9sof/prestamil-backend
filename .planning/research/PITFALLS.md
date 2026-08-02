# Pitfalls Research

**Domain:** Pawnshop (casa de empeño) loan-calculation engine and contract lifecycle — Prestamil v1.1 milestone
**Researched:** 2026-07-02
**Confidence:** HIGH (codebase-grounded — every pitfall below is traced to specific files/lines in `prestamil-backend`) / MEDIUM (general MariaDB/Liquibase concurrency and migration practices, verified via current sources)

This research is scoped to the concrete v1.1 milestone features in `.planning/PROJECT.md`: COCAE-exact gold table import, plata (silver) valuation, 2% weekly late-payment sanción, on-the-fly amortization, consecutive folio, reposición-in-caja bug fix, and beneficiario NOT NULL. Findings are grounded in the current state of `ContratoService`, `PlazoService`, `MovimientoContratoService`, and the Liquibase changelog — several of these features are **already partially implemented** (refrendo/sanción, reposición) and the pitfalls below include gaps found in that existing code, not just hypothetical ones.

## Critical Pitfalls

### Pitfall 1: Server trusts client-supplied `avaluoReal` — the "exact COCAE match" guarantee is meaningless if the valuation itself is spoofable

**What goes wrong:**
`ContratoService.buildPartida()` takes `avaluoReal` straight from `PartidaContratoRequest.getAvaluoReal()` (client input, only `@NotNull`-validated) and stores it as-is. `calcularPrestamoMaximo()` then computes the maximum authorized loan **from that same client-supplied value**:
```java
BigDecimal avaluo = pr.getAvaluoReal() != null ? pr.getAvaluoReal() : BigDecimal.ZERO;
// ... avaluo * porcPrestamoSAvaluo / 100, or avaluo directly
```
There is no server-side recomputation of `avaluoReal` from `PlazoHechuraAlhaja.precioPrestamo × pesoGramos` (gold) for the ALHAJA case. The only genuinely server-controlled number today is `avaluoContrato` (via `PlazoService.calcularAvaluoContrato`), which is derived from `montoPrestamo` — not the other way around. So an authenticated cajero (or anyone replaying the `POST /api/contratos` request with a modified body) can send any `avaluoReal` and unlock a higher `montoPrestamo` ceiling than the imported COCAE table would allow.

**Why it happens:**
The "avalúo vs préstamo separation" and "préstamo ajustable solo a la baja" business rules were designed around the *avalúo already being trustworthy*. Phase 3 built the request/response plumbing before Phase v1.1's job (server-authoritative gold table lookup) existed, so the trust boundary was never moved to the server.

**How to avoid:**
When implementing the exact gold-table motor (item 1), make the entry point `POST /api/contratos` (and any `/calcular` dry-run endpoint) recompute `avaluoReal` server-side by looking up `PlazoHechuraAlhaja` by `(idPlazo, sucursalId, kilataje, hechura)` and multiplying by `pesoGramos` — never trust `pr.getAvaluoReal()` for the loan-ceiling calculation. For PLATA, recompute from `precioGramoPlata × ley/1000 × pesoGramos`. For VARIOS, the valuador-entered number is legitimately the source of truth (no reference table exists) — but that should be an explicit, documented exception, not an accidental default that also applies to ALHAJA/PLATA.

**Warning signs:**
Any `PartidaContratoRequest` field that flows into a monetary ceiling calculation without a corresponding repository lookup in the same code path. Grep for `pr.getAvaluoReal()` and `pr.getPrecioXGramo()` usage in `ContratoService` — both are currently client-trusted.

**Phase to address:**
Same phase that implements the exact gold-table motor (item 1) and plata valuation (item 2) — do not ship the "exact match" table import without also moving `avaluoReal` computation server-side, or the fidelity work is cosmetic.

---

### Pitfall 2: BigDecimal scale/rounding drift makes "exact match to COCAE" fail even when the formula is right

**What goes wrong:**
The codebase already mixes several different rounding scales across the calculation chain:
- `PlazoHechuraAlhaja.precioBase`/`precioPrestamo`: scale 4, `PlazoService.recalcularRegistros` rounds with `RoundingMode.HALF_UP` at scale 10 for intermediate division, then scale 4 for storage.
- `Contrato.montoPrestamo`/`montoAvaluo`, `PartidaContrato.avaluoReal`/`avaluoContrato`/`montoPrestamo`: scale 2.
- `calcularAvaluoContrato()` divides the percentage factor at scale 6 before multiplying.
- `calcularAmortizacion()` computes `interesPeriodo` once at scale 2 and then multiplies by `n` for cumulative totals — this compounds a single rounding decision across every row instead of rounding each period independently (or vice versa), which may be a different rounding strategy than COCAE's, which likely rounds per-period and never re-derives from a multiplied base.
- The imported 8×3 COCAE `%Prestamo` table is "irregular" per PROJECT.md — if it's imported with fewer decimal places than COCAE's internal precision (e.g., stored as `DECIMAL(12,4)` but COCAE computes at a different internal scale before its own final rounding), amounts will match on some rows and silently drift by a few cents on others, especially for high `pesoGramos` values where rounding error scales with the multiplier.

**Why it happens:**
Each formula was implemented independently without a single documented "rounding contract" (which scale/mode applies at each step: per-gram price, per-partida avalúo, per-contrato total, per-period interest). BigDecimal's `equals()` is also scale-sensitive (`new BigDecimal("10.00").equals(new BigDecimal("10.0"))` is `false`), so naive "does it match COCAE" tests using `assertEquals` on BigDecimal instead of `compareTo()` will produce false failures (or worse, false passes if both sides happen to share scale despite different rounding).

**How to avoid:**
1. Before writing any new formula code, capture 15-20 real COCAE contract printouts (screenshots, per PROJECT.md's stated verification method) spanning different kilatajes, hechuras, and pesos, and reverse-engineer COCAE's rounding points (per-gram? per-partida? per-contrato?) rather than assuming.
2. Pin one rounding mode (`HALF_UP` is already the project convention) and **document the scale used at each intermediate step** in a single place (e.g., a comment block in `PlazoService`/`ContratoService`), not scattered per-method.
3. Round once at the smallest unit that COCAE prints (typically 2 decimals for pesos, but the intermediate gram-price table may need 4), and avoid re-deriving totals by multiplying an already-rounded per-unit value across many periods when COCAE computes rows independently (verify against captures).
4. In tests, always compare BigDecimals with `compareTo(...) == 0`, never `equals()`.

**Warning signs:**
Contract totals that match COCAE for round weights (e.g., 5.0000g) but drift by a cent for irregular weights (e.g., 3.7842g) — a classic symptom of rounding at the wrong step.

**Phase to address:**
Phase implementing the exact gold-table motor (item 1) — write the rounding contract and unit tests against captured COCAE values *before* wiring the amortization/sanción math on top of it, since those depend on `montoPrestamo` being cent-exact first.

---

### Pitfall 3: Naive `SELECT MAX + 1` (or the current PK-derived) folio strategy breaks under multi-turno/multi-sucursal concurrency

**What goes wrong:**
Today, `ContratoService.crearContrato()` saves the contract once (folio null), then derives the folio from the generated PK: `String.format("CTR-%06d", guardado.getId())` and saves again. This is safe for uniqueness *today* because it rides on InnoDB's atomic `AUTO_INCREMENT`, and `sucursalId` is hardcoded to `1` everywhere (`// TODO: derivar de la sucursal del usuario/turno`). The risk is what happens when:
- **Multi-sucursal folios are required to be a per-branch consecutive series** (a common pawnshop/COCAE convention — folios often run independently per sucursal, not as one global counter). The current PK-derived approach gives one global sequence across all branches; switching to a per-sucursal counter naively (`SELECT MAX(numero_local) FROM contrato WHERE id_sucursal = ?` then `+1`) reintroduces the classic **read-then-increment race condition**: two cajeros in the same sucursal, in concurrent transactions, both read the same `MAX`, both compute the same "next" number, and both commit — producing duplicate folios (a serious problem for a document with legal/audit weight).
- **`AUTO_INCREMENT` gaps on rollback**: InnoDB does not roll back the auto-increment counter when a transaction fails. If a future change pre-reserves an ID (e.g., inserting a draft row before validation completes) and then rolls back on a validation error, the folio sequence will show gaps. If the business expects gapless folios (as COCAE customers are used to), this is a support complaint waiting to happen.
- **Two-write window**: between the first `save()` (folio = NULL) and the second `save()` (folio set), any concurrent read (a report, a search-by-folio) that hits the row mid-transaction won't see it (fine today, same transaction) — but if this pattern is ever changed to two separate transactions for performance reasons, a contract could be briefly visible with `folio = NULL`, breaking any code that assumes `folio` is always non-null once a contract exists.

**Why it happens:**
The current implementation works by accident (single global counter, single sucursal) rather than by design for the concurrent multi-branch case the milestone explicitly targets ("bajo sesiones concurrentes de cajero, multi-turno, multi-sucursal").

**How to avoid:**
If per-sucursal folios are required: add a dedicated counter table, e.g. `folio_secuencia(id_sucursal INT PRIMARY KEY, ultimo_folio INT NOT NULL)`, and claim the next number with an atomic `UPDATE folio_secuencia SET ultimo_folio = ultimo_folio + 1 WHERE id_sucursal = ?` followed by `SELECT ultimo_folio` **inside the same transaction as the contract INSERT**, relying on the row lock the `UPDATE` takes (released only at commit) to serialize concurrent cajeros — this is the standard safe pattern for MariaDB/MySQL gapless-per-key counters, safer than `SELECT ... FOR UPDATE` on the contrato table itself (which would serialize *all* contract creation, not just folio assignment) and safer than MariaDB's native `SEQUENCE` objects (which explicitly do not guarantee gaplessness and are awkward to scope per-sucursal). If the global PK-derived approach is kept (single series across all branches), confirm with the business that this is acceptable *before* building UI/reports around a per-branch assumption.

**Warning signs:**
Duplicate-folio errors surfacing only under load/multi-cajero testing (won't show up in single-user manual QA); any code that assumes `folio` numeric suffix increases monotonically per-sucursal.

**Phase to address:**
Handle in the *same phase* as folio generation — do not ship folio work in this milestone without deciding (and testing under concurrency) the per-sucursal vs global question, since retrofitting a counter table after cajeros are already relying on folio numbers is a painful migration.

---

### Pitfall 4: Reposición fee already flows into `movimiento_contrato`/turno correctly — the bug is more likely in a report layer that doesn't exist yet, or double-counts

**What goes wrong:**
`MovimientoContratoService.cobrarReposicion()` already correctly creates a `MovimientoContrato` with `tipo = REPOSICION_CONTRATO`, linked to the **active turno** (not the contract's original turno) — this is the right design for "corte de caja" (cash reconciliation should reflect what happened *during a turno*, regardless of when the underlying contract was opened). However:
- No caja/corte-de-turno aggregation report exists anywhere in the codebase yet (no `CorteCajaController`, no Jasper template beyond `contrato.jrxml`, no query summing `movimiento_contrato` by `id_turno`). Whatever report is built to fix the "known discrepancy" bug must aggregate by `movimiento_contrato.id_turno`, not `contrato.id_turno` — using the wrong FK would attribute reposición income to the turno when the *original loan* was made instead of the turno when the *reposición fee was actually collected*, which is a very common root cause of "cobrado ≠ reportado" bugs in cash-register reconciliation systems.
- `TipoMovimiento` is a growing enum (`REFRENDO`, `REFRENDO_EXTEMPORANEO`, `FINIQUITO`, `FINIQUITO_EXTEMPORANEO`, `ABONO`, `REPOSICION_CONTRATO`). Any report/aggregation logic that hardcodes an `IN (...)` filter or a `switch` over movement types (rather than iterating exhaustively or using a default `else` bucket) will silently exclude `REPOSICION_CONTRATO` (or any future type) the moment someone forgets to update the list.
- Double-counting risk: if the eventual report shows both a per-contrato movement list *and* a turno-level summary that re-derives totals independently (e.g., one from `movimiento_contrato.monto` sum, another from a running "caja" balance updated imperatively elsewhere), any drift between the two code paths reproduces the exact bug being fixed.

**Why it happens:**
Cash reconciliation reports are usually built by summing "the obvious" transaction types first (interest, abonos) and reposición/reprint fees get added later as an afterthought, in a different code path that isn't wired into the same aggregation query.

**How to avoid:**
Build the corte-de-caja report as a **single query source of truth**: `SELECT tipo, SUM(monto) FROM movimiento_contrato WHERE id_turno = ? GROUP BY tipo`, and require every consumer (PDF, on-screen summary, any "total de caja" figure) to derive from that same query — never maintain a parallel running total. Write a test that asserts the sum of all `TipoMovimiento` values reconciles with the turno total, so adding a new enum value without updating the report fails CI rather than failing silently in production.

**Warning signs:**
A report total that's a fixed formula (`interes + abonos + finiquitos`) instead of `SUM(monto) GROUP BY tipo` over the actual enum; any place enumerating movement types by name instead of iterating the enum.

**Phase to address:**
Phase implementing "reimpresión y reposición de contrato con cobro correcto en caja/reportes" — since the `cobrarReposicion()` charge-side logic is already correct, this phase's actual work is the *report* side; verify the fix by writing the reconciliation test described above before considering the bug closed.

---

### Pitfall 5: Adding NOT NULL to `nombre_beneficiario` on a live table without backfilling existing rows breaks the Liquibase deploy

**What goes wrong:**
`contrato.nombre_beneficiario VARCHAR(200)` is nullable today, and `ContratoService.crearContrato()` only sets it when `request.getNombreBeneficiario()` is non-null — the field currently has no `@NotBlank` on `ContratoRequest`. Existing contracts from Phase 3 development/testing (and the seed data in `009-clientes-prueba.sql`, plus any real contracts created before this milestone ships) will have `NULL` values. A Liquibase `addNotNullConstraint` changeset without a `defaultNullValue` will **fail to apply** against any environment that already has such rows — this is the kind of migration that works fine on a fresh dev DB but breaks on QA/prod the moment it's deployed against real data.

**Why it happens:**
"Beneficiario obligatorio" was decided in a business meeting *after* the field already shipped as optional (per `AVALUOS.md` section 7, question 3 — this was an open question, now resolved). Schema and validation were designed around the old assumption.

**How to avoid:**
1. Do NOT write a bare `addNotNullConstraint` changeset. Either:
   - Use `addNotNullConstraint` with `defaultNullValue` set to an explicit placeholder agreed with the business — acceptable here since the table is small (dev/test scale, not millions of rows), so a single-statement backfill+constrain is safe and doesn't need a batched expand/contract pattern.
   - Or split into two changesets: an explicit `UPDATE contrato SET nombre_beneficiario = '...' WHERE nombre_beneficiario IS NULL`, then a separate `addNotNullConstraint` with no default — preferred for auditability (the backfill value is visible in changelog history, not buried in a change-type attribute).
2. Add `@NotBlank` to `ContratoRequest.nombreBeneficiario` **in the same phase**, not before or after — if the DB constraint lands before the DTO validation, cajeros hit a raw 500/`DataIntegrityViolationException` instead of a clean 400 `BadRequestException`; if the DTO validation lands first without the DB constraint, the "obligatorio" rule is only enforced by one API entry point and any direct-entity endpoints or future bulk-import paths can still write nulls.
3. Decide explicitly whether "beneficiario obligatorio" means `nombre_beneficiario` (free text, always required) or extends to requiring `id_beneficiario` (FK to an existing `Cliente` record) — the two are currently independent columns in `Contrato`, and conflating them changes the migration (FK NOT NULL is a different, riskier change than a VARCHAR NOT NULL, since it requires every historical contract to resolve to an actual `Cliente` row, not just a string).

**Warning signs:**
Liquibase deploy failing on QA/staging with a constraint-violation error that never appeared on a fresh local DB; a 500 error (not 400) surfacing in the frontend when a cajero forgets to fill beneficiario.

**Phase to address:**
Phase implementing "beneficiario obligatorio" — write the backfill changeset, the DTO validation, and confirm which column(s) the rule applies to, all together. Do not let the DB constraint ship in an earlier or later phase than the request validation.

---

### Pitfall 6: Plata (silver) valuation risks reusing the gold per-kilate formula instead of a per-ley formula

**What goes wrong:**
`PlazoParametro` already has `ley925`, `ley725`, and `precioGramoPlata` columns (added in changeset `011-4`), and `PartidaContrato.ley` exists — but **no service method computes anything from them yet**. The existing, working gold formula in `PlazoService.recalcularRegistros()` scales linearly by `kilataje` against a 24K base (`precioPorKilatePuro * kilataje`). Silver purity is conventionally expressed as **ley** (a fineness ratio, e.g., 925/1000 = 92.5% pure, or 725/1000 = 72.5%), not as a "kilataje out of 24" scale. If the plata calculation is implemented by copy-pasting the gold formula and treating `ley` as if it were `kilataje` (e.g., dividing by 24 instead of by 1000), the resulting price will be wrong by a large, non-obvious factor that won't show up until compared against a real COCAE plata contract.

**Why it happens:**
Both gold and silver are "precious metal by purity," which invites code reuse, but the reference bases differ (24 for gold kilataje, 1000 for silver ley) and the *hechura* factor concept may or may not apply the same way to silver in COCAE (unconfirmed — flagged as an open question in PROJECT.md's "Investigación en curso").

**How to avoid:**
Do not extend `recalcularRegistros()` to silver by parameterizing `baseKilataje` alone. Confirm from COCAE captures whether silver pricing is `precioGramoPlata × (ley / 1000)` with or without a hechura/factor multiplier, and implement it as an explicit, separately-tested formula (e.g., `calcularPrecioPlata(ley, precioGramoPlata)`), even if it shares infrastructure (a `PlazoHechuraAlhaja`-style table) with gold.

**Warning signs:**
Silver contract loan amounts that are off by roughly a factor of 24/1000 (≈41x) or 1000/24 (≈0.024x) from expected — a strong signal the wrong base was used.

**Phase to address:**
Phase implementing plata (silver) valuation (item 2) — get COCAE captures for at least one 925 and one 725 example before writing the formula, per the project's own stated verification approach for gold.

---

### Pitfall 7: Sanción (2% weekly) and refrendo extension compound in ways that may not match COCAE's own rounding/period boundaries

**What goes wrong:**
`MovimientoContratoService.calcularSemanasVencidas()` computes `diasVencido = daysBetween(fechaVencimiento, now) - diasGracia` then `Math.ceil(diasVencido / 7.0)`. This rounds *any* partial week up to a full week's sanción (e.g., 1 day late after grace = charged a full week's 2%). That may or may not match COCAE's actual behavior — some legacy systems charge sanción per elapsed day pro-rated, others per started week (as implemented here), and getting this wrong means a client is either overcharged or undercharged relative to what they'd pay at the legacy system, which is exactly the kind of discrepancy this milestone is trying to eliminate. Separately, `refrendar()` extends `fechaVencimiento` by exactly one `diasPorPeriodo` regardless of how many `semanasVencidas` had accumulated — meaning a contract that was several weeks overdue and gets refrendado only advances one period forward from its *old* due date, potentially leaving it still "in the past" relative to today if `diasPorPeriodo` is short (e.g., the 1-day "Diario" plazo added in changeset `011-6`), which would make the very next read of the contract show it as still vencido immediately after refrendo.

**Why it happens:**
The sanción formula and the due-date extension were implemented as two independent pieces of logic without a shared invariant ("after refrendo, is the contract guaranteed to be current?").

**How to avoid:**
Verify the weekly-rounding rule (ceil vs floor vs pro-rata) against a real overdue COCAE refrendo capture before trusting the current `Math.ceil` implementation. For the due-date extension, consider whether `fechaVencimiento` should advance from `max(fechaVencimiento, hoy)` or advance by enough periods to catch up to "today," rather than always exactly one period, especially for short-period plazos (Diario) where one refrendo may not be enough.

**Warning signs:**
A contract still showing `estatus = VENCIDO`-eligible (past due) immediately after a refrendo was just registered, for short-period plazos.

**Phase to address:**
Phase implementing the 2% weekly sanción (item 3) — this logic already exists and works for simple cases, so this is a verification/refinement task against COCAE captures rather than new-build, but should be closed out explicitly rather than assumed correct because it compiles and has a plausible-looking test.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Deriving folio from the `contrato.id` auto-increment PK instead of a dedicated sequence table | Zero extra schema, trivially safe today (single sucursal) | Breaks the moment per-sucursal folio series or gapless-folio guarantees are required; hard to retrofit once cajeros/reports depend on the numbering scheme | Only while `sucursalId` stays hardcoded to `1` and folio gaps are explicitly accepted by the business |
| Storing `avaluoReal` as client-trusted input | Faster to ship the initial Avaluos UI (Phase 3) | Undermines the entire "motor de cálculo exacto" premise of this milestone; a real integrity gap, not just a rounding nuance | Never acceptable once the COCAE-exact gold table is the system of record — must close before milestone completion |
| Rounding at multiple different scales (2, 4, 6, 10) across the calculation chain without a documented contract | Each method "just works" in isolation | Cent-level drift vs COCAE that's expensive to debug because it only appears on some weight/kilataje combinations | Acceptable only if each scale choice is deliberate and documented; currently it looks incidental |
| `sucursalId = 1` hardcoded in `ContratoService.crearContrato()` | Simplifies Phase 3 delivery | Silently wrong the moment a second sucursal is onboarded — contracts, folios, and turno linkage would all misattribute | Acceptable short-term only with a tracked TODO (already present in code) and only before multi-sucursal go-live |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|-------------------|
| Legacy COCAE `%Prestamo` table (manual transcription from screenshots) | Transcribing values as displayed (rounded for screen) instead of the underlying stored precision, causing rows to "look right" but sum wrong across large weights | Cross-check transcribed values against at least 2-3 real printed COCAE contracts' final montos, not just the config-screen table, to catch screen-rounding vs stored-precision mismatches |
| JasperReports (`contrato.jrxml`) BigDecimal formatting | Locale-default number formatting can drop trailing zeros or misformat thousands separators, making the printed contract disagree with the DB value even though the underlying calculation was correct | Explicitly set a `java.text.DecimalFormat`/pattern in the Jasper template pinned to 2 decimals, don't rely on default locale formatting |
| Liquibase changesets on an already-migrated environment | Writing a new changeset that assumes a clean slate (e.g., blind `INSERT`) when QA/prod already has rows from the Phase 3 seed data (`009-clientes-prueba.sql`) or real usage | Use `INSERT IGNORE` (already the project's convention per `011-6`) or explicit `WHERE NOT EXISTS` guards for any changeset touching rows that may already exist; always test against a QA snapshot, not just a fresh DB |
| `PlazoParametro` lookup by `(plazo, tipoPrenda, sucursal)` composite key | `obtenerParametro()` in `MovimientoContratoService` silently returns `null` (no sanción, no reposición) when the combination isn't configured — a missing config row fails silently as "sanción disabled" rather than erroring | Consider whether missing config should be a hard error (`ResourceNotFoundException`) for financially significant paths like sanción/reposición, rather than a silent no-op, especially once these are the system of record instead of a fallback |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| `recalcularTodasLasTablas()` reloads and rewrites every `PlazoHechuraAlhaja` row per sucursal on every gold-price change | Fine today (24 rows × few plazos); becomes a large `saveAll` if more kilatajes/hechuras/plazos are added later | Keep the recalculation scoped and batched (already using `saveAll`); if it grows, consider a bulk `UPDATE ... JOIN` SQL statement instead of entity-by-entity save | Only relevant if the table grows into the hundreds/thousands of rows (multi-sucursal × many plazos × many kilataje/hechura combos) — not a near-term concern at current scale |
| `calcularAmortizacion()` builds the full period list in memory for every request | Negligible now (max ~30 periods for "Diario" plazo) | Keep as on-the-fly (already the design) — do not persist rows | Would only matter for a plazo with hundreds of periods, unlikely for a pawnshop |
| `buildPartida()` does one repository round-trip per partida (tipoPrenda, plazoParametro, catValorPrenda) inside a loop | Extra DB round-trips per contract, scaling with number of partidas | Contracts typically have few partidas (1-5), so this is currently a non-issue; batch-fetch if contracts with many partidas become common | Not a near-term concern given typical pawnshop ticket size |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Client-trusted `avaluoReal` used to compute the loan ceiling (Pitfall 1) | An authenticated cajero (or a compromised session) can request loans above the policy-authorized maximum by manipulating the API payload directly, bypassing the "never above calculated limit" business rule that is a core value proposition of this milestone | Recompute `avaluoReal` server-side from the imported gold/silver tables for ALHAJA/PLATA; only trust client input for VARIOS where no reference table exists |
| Reposición fee amount (`param.getMontoReposicion()`/`porcReposicion`) is read from `PlazoParametro` (server config) — currently safe | N/A today | Keep this server-derived; do not let a future UI accept a client-supplied override for the reposición amount without an explicit authorization/role check |
| `sucursalId` hardcoded rather than derived from the authenticated user's session/turno | Once multi-sucursal is live, a cajero's request could theoretically be misattributed to the wrong branch if the hardcoded value isn't replaced everywhere consistently | Derive `sucursalId` from `Turno`/session in one place (a helper/service method) rather than scattering the hardcoded `1` across controllers, so the eventual fix is a single change point |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Rejecting `montoPrestamo` above the server-computed max with a generic `BadRequestException` message | Cajero sees a validation error after already filling the whole form, without a clear proactive cap on the field itself | Include the computed max in the error response (already partially done — the message interpolates `prestamoMaximo`) and mirror the same max in the frontend proactively so the field is capped before submit, not just rejected after |
| Sanción/refrendo silently computing `0` when `PlazoParametro` is missing for the contract's tipoPrenda/sucursal combo | Cajero registers a refrendo believing sanción was correctly waived, when actually it's a missing-config bug | Surface a warning (not necessarily a hard block) when `obtenerParametro()` returns null for a financially significant operation, so missing config is visible instead of silently absorbed |
| Beneficiario becoming a required field with no migration messaging | Cajeros hit a new mandatory field on contracts they're used to skipping, without explanation | Add a short inline hint/tooltip explaining why beneficiario is now required (ties to the "aclarado en reunión con Jorge" business context) when the frontend form changes |

## "Looks Done But Isn't" Checklist

- [ ] **Motor de oro exacto:** Looks done once `PlazoHechuraAlhaja` is populated with the imported COCAE table — but if `avaluoReal` is still client-computed (Pitfall 1), the "exact match" guarantee doesn't actually hold end-to-end. Verify by attempting to POST a contract with a spoofed `avaluoReal` and confirming the server rejects/recomputes it.
- [ ] **Sanción 2% semanal:** Looks done because `MovimientoContratoService.refrendar()` already compiles and computes a plausible sanción — verify the weekly-rounding rule (ceil vs pro-rata) and the due-date extension against real overdue COCAE captures, not just that the code runs.
- [ ] **Reposición en caja:** Looks done because `cobrarReposicion()` correctly writes a `MovimientoContrato` linked to the turno — verify by building the actual corte-de-caja report and confirming `SUM` reconciles, since no such report exists yet to prove the "known bug" is actually fixed.
- [ ] **Folio consecutivo:** Looks done because folios are unique and increasing today — verify specifically under concurrent multi-cajero load in the target sucursal scope (single global counter vs per-branch), not just single-user manual testing.
- [ ] **Beneficiario obligatorio:** Looks done once `@NotBlank` is added to the DTO — verify the Liquibase migration actually applies cleanly against an environment with existing NULL rows (QA/staging snapshot, not a fresh local DB).
- [ ] **Plata (925/725):** Looks done once a `ley`-based formula compiles and returns numbers — verify against real COCAE plata contract captures that the base (1000, not 24) and any hechura-equivalent factor are correct, not just that the formula is dimensionally plausible.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|-----------------|
| Client-trusted `avaluoReal` already used in production contracts | MEDIUM | Add server-side recomputation going forward; historical contracts keep their original (possibly untrusted) `avaluoReal` — flag them for manual audit if fraud/error is suspected, don't attempt to silently rewrite historical financial records |
| Duplicate folios discovered after go-live (race condition) | MEDIUM | Add a unique constraint on `folio` (already present per schema) so duplicates fail loudly going forward at the DB level; introduce the counter-table pattern and manually resolve the small number of existing collisions with a suffix or manual renumber, documented for audit |
| NOT NULL migration fails on QA/prod due to existing NULLs | LOW | Backfill with an explicit `UPDATE ... WHERE nombre_beneficiario IS NULL` changeset before re-running the `addNotNullConstraint` changeset; Liquibase changesets are idempotent once marked run, so this is a forward-fix (new changeset), not a rollback |
| Caja report found to double-count or omit reposición after go-live | MEDIUM | Rebuild the report from the single `GROUP BY tipo` source-of-truth query (Pitfall 4); reconcile historical turnos by re-running the corrected query against `movimiento_contrato` history (data isn't lost, just needs re-aggregation) |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| Client-trusted `avaluoReal` (Pitfall 1) | Same phase as exact gold-table motor (item 1) | Attempt to POST a contract with a spoofed `avaluoReal` in a test; confirm server recomputes/rejects it |
| BigDecimal scale/rounding drift (Pitfall 2) | Same phase as exact gold-table motor (item 1), before sanción/amortización are layered on top | Unit tests comparing computed montos to real COCAE captures using `compareTo(...) == 0`, across varied weights |
| Folio race condition (Pitfall 3) | Same phase as folio generation — do not defer | Concurrency test: N parallel `crearContrato` calls (same sucursal), assert N unique folios, no duplicates/deadlocks |
| Reposición-in-caja report gap (Pitfall 4) | Same phase as "reimpresión y reposición con cobro correcto en caja/reportes" | Build the `GROUP BY tipo` corte-de-caja query/report and write a reconciliation test asserting sum(movimientos) == report total, across all `TipoMovimiento` values |
| Beneficiario NOT NULL migration (Pitfall 5) | Same phase as "beneficiario obligatorio" | Run the Liquibase changeset against a QA/staging snapshot with pre-existing NULL rows, not just a fresh DB; confirm API returns 400 (not 500) for missing beneficiario |
| Plata per-ley vs per-kilate confusion (Pitfall 6) | Same phase as plata valuation (item 2) | Compare computed silver loan amounts against at least one real 925 and one real 725 COCAE capture before merging |
| Sanción rounding / refrendo due-date catch-up (Pitfall 7) | Same phase as 2% weekly sanción (item 3) | Test a contract multiple periods overdue on a short plazo (e.g., Diario); confirm refrendo makes it current, not still-vencido |

## Sources

- Codebase inspection (HIGH confidence — direct read): `ContratoService.java`, `PlazoService.java`, `MovimientoContratoService.java`, `Contrato.java`, `PartidaContrato.java`, `PlazoParametro.java`, `PlazoHechuraAlhaja.java`, `MovimientoContrato.java`, `TipoMovimiento.java`, `ContratoRepository.java`, `PartidaContratoRequest.java`, Liquibase changesets `007-contratos.sql` and `011-oro-sancion-plata.sql`, `.planning/codebase/AVALUOS.md`, `.planning/PROJECT.md`
- [A Developer's Guide to MariaDB Auto-Increment Issues and Sequence Workarounds](https://runebook.dev/en/docs/mariadb/auto_increment-faq/index) — MEDIUM confidence, verifies AUTO_INCREMENT gap behavior and multi-node caveats
- [MariaDB Sequences documentation](https://mariadb.com/kb/en/sequences/) — MEDIUM confidence, confirms native `SEQUENCE` objects don't guarantee gaplessness
- [How to Implement a Sequence Generator in MySQL](https://oneuptime.com/blog/post/2026-03-31-mysql-sequence-generator/view) — MEDIUM confidence, confirms the UPDATE-then-SELECT row-locking counter-table pattern as the standard safe approach
- [Liquibase addNotNullConstraint reference](https://docs.liquibase.com/change-types/add-not-null-constraint.html) — HIGH confidence (official docs), confirms `defaultNullValue` backfill mechanism and failure mode without it
- [Database Migrations Without Drama: Expand/Contract in Practice](https://blogs.reliablepenguin.com/2025/11/16/database-migrations-without-drama-expand-contract-in-practice) — MEDIUM confidence, general batched-backfill best practice (noted as not strictly necessary at Prestamil's current table scale, but the underlying principle of backfill-before-constrain applies)

---
*Pitfalls research for: Prestamil v1.1 — Motor de Cálculo Real y Ciclo de Vida del Contrato*
*Researched: 2026-07-02*
