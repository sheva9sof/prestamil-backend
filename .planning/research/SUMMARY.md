# Project Research Summary

**Project:** Prestamil v1.1 — Motor de Cálculo Real y Ciclo de Vida del Contrato
**Domain:** Pawnshop (casa de empeño) contract lifecycle & financial calculation engine — legacy-parity money math for a Spring Boot 3.2.5 + Angular 20 SPA
**Researched:** 2026-07-02
**Confidence:** MEDIUM-HIGH

## Executive Summary

Prestamil v1.1 is not a greenfield build — it is a **correction and completion pass** over an existing, partially-implemented contract-lifecycle engine. Architecture research found that 6 of the 8 target features already have a first implementation in the codebase (gold table, silver columns, amortization, sanción, folio, reposición ledger entry), which materially changes the shape of this milestone: most work is fixing wrong logic and closing integrity gaps in existing services (`PlazoService`, `ContratoService`, `MovimientoContratoService`), not building new modules. No new runtime dependencies, frameworks, or architectural layers are needed — every capability (exact-decimal money math, on-the-fly amortization, concurrency-safe folios, date-based penalties) is already covered by `BigDecimal`, `java.time`, Spring Data JPA, and MariaDB `AUTO_INCREMENT`, all used correctly elsewhere in the codebase already.

The recommended approach is disciplined application of existing patterns plus two categories of fixes: **(1) legacy-parity fidelity** — importing COCAE's real, irregular 24-cell gold table instead of deriving it from 3 global hechura factors, and wiring the already-present-but-unused silver (`ley925`/`ley725`) fields into an actual calculation — and **(2) trust-boundary/integrity fixes** — moving `avaluoReal` computation from client-trusted input to server-side recomputation for ALHAJA/PLATA, making `beneficiario` mandatory with a safe backfill migration, and building the first-ever corte-de-caja report so the reposición-charging bug can actually be verified fixed (no such report exists today). Regulatory research (CONDUSEF/PROFECO/NOM-179-SCFI-2016) confirms there is no statutory rate cap in Mexico today, so the 2%/semana sanción is legally permissible provided it stays disclosed and configurable — validating the existing data-driven design.

The single largest risk is **legacy-parity precision**: several formulas (gold table cells, silver ley-to-price conversion, sanción week-rounding) cannot be verified correct without real COCAE screenshots showing intermediate calculated values, not just final totals — this is explicitly flagged as a pending business-data gap in PROJECT.md, not a stack or architecture risk. The second-largest risk is a **security/integrity gap already in production**: the server currently trusts client-supplied `avaluoReal` for loan-ceiling calculations, which undermines the "exact match" premise regardless of how correct the underlying table import is — this must be closed in the same phase as the gold/silver table work, not treated as a separate hardening task.

## Key Findings

### Recommended Stack

No new runtime dependencies are needed for this milestone. The correct stack is the one already in use: `java.math.BigDecimal` (string-constructed, explicit `RoundingMode.HALF_UP`, explicit `.setScale()`) for all money/percentage math, `java.time`/`ChronoUnit` for date and penalty-period math, Liquibase SQL-formatted numbered changesets for schema and seed-data changes (COCAE table import), and MariaDB InnoDB `AUTO_INCREMENT` for concurrency-safe folio derivation (already atomic and correct as implemented). Explicitly rejected additions: `javax.money`/JSR-354, Quartz Scheduler, MariaDB `SEQUENCE` objects, a persisted amortization table, Joda-Time, and generic audit-trail frameworks — all would be over-engineering relative to this single-branch, single-server, low-throughput system's actual needs.

**Core technologies:**
- `java.math.BigDecimal` (JDK 21, string-constructed): exact-decimal money/percentage math — already the project convention; must never accept `double`-sourced values.
- `java.time` (`LocalDate`, `ChronoUnit`): date math for vencimientos, refrendos, sanciones — `ChronoUnit.DAYS.between(...)` is the correct primitive, already used.
- Liquibase numbered SQL changesets (4.27.0): import the COCAE gold table and any new columns/constraints — matches the existing 11-changeset convention exactly.
- MariaDB InnoDB `AUTO_INCREMENT`: concurrency-safe folio generation — already correctly implemented via the existing save-derive-save pattern in `ContratoService.crearContrato()`.

### Expected Features

Regulatory research (CONDUSEF/PROFECO/NOM-179-SCFI-2016, MEDIUM-HIGH confidence) confirms casas de empeño are consumer-protection-regulated (not banking-regulated), and the boleta de empeño has specific mandatory disclosure fields (folio consecutivo, tasa de interés, gastos, sanción/refrendo terms, firma). No interest/penalty rate ceiling currently exists in law, though 2025 legislative proposals (36%/año) are pending — reinforcing that rates should stay data-driven, never hardcoded (already the architecture).

**Must have (table stakes) — P1, this milestone:**
- Cálculo de plata por ley (925/720 — verify "725" vs "720" against real COCAE data)
- Cálculo de Varios/electrónicos con lógica propia (already implemented correctly)
- Sanción 2% semanal por refrendo, visible en contrato impreso (implemented; rounding rule needs COCAE verification)
- Tabla de amortización/vencimientos calculada al vuelo (implemented, no persistence)
- Folio automático consecutivo (implemented for single-sucursal; concurrency-safe by InnoDB design)
- Reposición/reimpresión de contrato con cobro correcto en caja (ledger-side implemented; report side missing entirely)

**Should have (competitive) — P2, right after P1:**
- Reporte/PDF de referencia offline (reuse existing JasperReports infra, build once calc engines are stable)
- Ajuste de plantilla de impresión a hoja oficio (depends on finalized field list)

**Defer (v2+):**
- CAT (Costo Anual Total) calculator/disclosure — needs dedicated NOM-179 Anexo legal review, not requested by client
- Statutory rate-ceiling enforcement — no current law requires it; keep rates configurable
- Multi-sucursal folio numbering strategy — not needed while single-branch

### Architecture Approach

No new architectural layer or pattern is required — every gap is closed by adding methods to existing services (`PlazoService`, `ContratoService`, `MovimientoContratoService`) or, at most, one new lightweight service (`ReporteService`) and one new Liquibase changeset (beneficiario NOT NULL + backfill). The system already follows Controller to Service (`@Transactional`) to Repository (`BaseRepository`/JPA) to MariaDB, and this milestone stays entirely within that shape.

**Major components:**
1. `PlazoService.recalcularRegistros()` — fix to stop deriving gold hechura cells from 3 global factors; use per-cell imported `porcAumento` instead (COCAE parity fix).
2. `ContratoService.buildPartida()`/`calcularPrestamoMaximo()` — add server-side `avaluoReal` recomputation for ALHAJA (gold lookup) and PLATA (new `calcularAvaluoPlata()` method reading `ley925`/`ley725`/`precioGramoPlata`); close the client-trust gap.
3. `MovimientoContratoService` — already correctly implements refrendo/sanción/reposición ledger writes; needs formula verification against COCAE captures, not new logic.
4. New `ReporteService`/`ReporteController` — first-ever corte-de-caja aggregation (`GROUP BY tipo` over `MovimientoContrato`, scoped by `id_turno`) to make the reposición-in-caja fix verifiable.
5. New `ContratoReporteService` — wires the already-present but unused `contrato.jrxml` via JasperReports for PDF/reimpresión, gated behind reposición charge logic when reprinting.

### Critical Pitfalls

1. **Server trusts client-supplied `avaluoReal`** — the "exact COCAE match" guarantee is meaningless while a cajero/compromised session can submit an arbitrary `avaluoReal` to unlock a higher loan ceiling. Fix: recompute server-side from `PlazoHechuraAlhaja` (gold) and the new silver formula in the same phase as the table import — never ship "exact match" cosmetically.
2. **BigDecimal scale/rounding drift** — the codebase currently mixes scales (2, 4, 6, 10) across the calculation chain with no single documented rounding contract; COCAE parity requires matching where legacy rounds, not just final totals. Fix: capture 15-20 real COCAE printouts, document one rounding contract, always compare BigDecimals with `compareTo()` not `equals()`.
3. **Folio race condition if per-sucursal series are ever required** — today's PK-derived folio is safe only because there's one global sucursal; a naive `SELECT MAX+1` per-branch approach would reintroduce a classic race condition. Fix: if multi-sucursal folios are needed, use an atomic `UPDATE folio_secuencia SET ultimo_folio = ultimo_folio + 1` counter-table pattern inside the same transaction — decide and test this now, not after cajeros depend on the numbering.
4. **Reposición-in-caja "bug" likely has no report to prove it's fixed** — `cobrarReposicion()` already writes correctly to `MovimientoContrato` scoped by turno, but zero caja/corte-de-turno report exists anywhere in the code. Fix: build a single `GROUP BY tipo` source-of-truth query: no parallel running totals, and write a reconciliation test that fails when a new `TipoMovimiento` value is added without being included.
5. **NOT NULL beneficiario migration will fail on QA/prod without backfill** — existing seed/real contracts have NULL `nombre_beneficiario`; a bare `addNotNullConstraint` changeset works on a fresh DB but breaks against real data. Fix: explicit backfill `UPDATE` changeset before/alongside `addNotNullConstraint`, plus matching `@NotBlank` DTO validation in the same phase (not before/after, to avoid raw 500s).

## Implications for Roadmap

Based on research, suggested phase structure (dependency-aware, following architecture research's explicit build-order recommendation):

### Phase 1: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza
**Rationale:** Every avalúo/préstamo amount for ALHAJAS, every amortización row, and any future PDF/report embeds wrong numbers until the gold table is fixed. This is also the only item PROJECT.md flags as currently blocking. Must ship together with the server-side `avaluoReal` recomputation fix (Pitfall 1) — importing a correct table while still trusting client-sent avalúo is cosmetic, not real fidelity.
**Delivers:** Per-cell (not factor-derived) `PlazoHechuraAlhaja.porcAumento` recalculation logic; server-side avalúo recomputation for ALHAJA at contract-creation time; documented rounding contract (Pitfall 2) with `compareTo()`-based unit tests against real COCAE captures.
**Addresses:** "Cálculo de préstamo por oro con tabla real de 24 celdas" (FEATURES.md table stakes)
**Avoids:** Pitfall 1 (client-trusted avaluoReal), Pitfall 2 (rounding drift)

### Phase 2: Beneficiario Obligatorio (small, isolated, do early)
**Rationale:** Trivial and isolated — no dependency on anything else in the milestone. Doing it early stops accumulating more nullable-beneficiario contracts while other phases are in progress.
**Delivers:** Liquibase backfill + `addNotNullConstraint` changeset (two-step, auditable), `@NotBlank` on `ContratoRequest.idBeneficiario`, unconditional handling in `ContratoService.crearContrato()`, required frontend field.
**Uses:** Liquibase numbered SQL changeset pattern (STACK.md)
**Avoids:** Pitfall 5 (NOT NULL migration failure on QA/prod)

### Phase 3: Motor de Plata (leyes 925/720)
**Rationale:** Depends on nothing else in this milestone; can run in parallel with Phase 1 by a different work stream since it touches a different code path (`buildPartida` silver branch) and different fields (`ley925`/`ley725`/`precioGramoPlata` vs `PlazoHechuraAlhaja`).
**Delivers:** `ContratoService.calcularAvaluoPlata()` server-side formula (ley/1000 base, NOT ley/24 — Pitfall 6), wired into `buildPartida()` for PLATA tipoPrenda, replacing client-trusted avalúo for this category same as gold.
**Implements:** `ContratoService` new private method (ARCHITECTURE.md §3.2)
**Avoids:** Pitfall 6 (per-ley vs per-kilate formula confusion), Pitfall 1 (extends the trust-boundary fix to silver)

### Phase 4: Sanción 2% Semanal — Verification & Refinement
**Rationale:** Already functionally implemented (`MovimientoContratoService.refrendar()`); this phase is verification against COCAE captures and closing the due-date-catch-up edge case, not new-build.
**Delivers:** Confirmed weekly-rounding rule (ceil vs pro-rata) against real overdue COCAE captures; fix for `fechaVencimiento` extension not catching up on short-period (Diario) plazos after multiple overdue periods; sanción visibly disclosed on printed contract per NOM-179/LFPC.
**Addresses:** "Sanción 2% semanal por refrendo extemporáneo" (FEATURES.md, explicit client + regulatory requirement)
**Avoids:** Pitfall 7 (sanción rounding / refrendo due-date catch-up)

### Phase 5: Corte de Caja / Reporte (new, foundational for Phase 6)
**Rationale:** Should come after Phases 1-3 are stable — the report's whole value is showing trustworthy totals; building it against still-wrong gold/silver numbers means re-verifying later. This is the actual fix for the "known bug" (PROJECT.md), since no such report currently exists at all.
**Delivers:** New `ReporteService`/`ReporteController` (`GET /api/reportes/corte-caja/{turnoId}`), single `GROUP BY tipo` query over `MovimientoContrato` scoped by `id_turno` (not `contrato.id_turno`), reconciliation test asserting sum reconciles across all `TipoMovimiento` values.
**Implements:** New lightweight service, no new entity (ARCHITECTURE.md §2, §3.5)
**Avoids:** Pitfall 4 (report gap / wrong FK / double-counting risk)

### Phase 6: PDF / Reimpresión de Contrato
**Rationale:** Depends on Phase 2 (beneficiario must be present/required before assuming it's always printable) and benefits from Phases 1/3 being correct (trustworthy printed amounts) and Phase 5 existing (reimpresión's cash-register correctness is strongest once corte de caja actually reads `MovimientoContrato`). Cheapest to build last since its only job is printing what other engines compute.
**Delivers:** `ContratoReporteService` wired to existing `contrato.jrxml`/`contrato.jasper`, new `GET /api/contratos/{id}/pdf` endpoint; reimpresión = conditional reposición charge (Phase already built) + PDF regeneration through the same code path — resolves the caja-discrepancy bug by construction.
**Addresses:** "Reporte/PDF de referencia offline", "Reimpresión/reposición con cobro correcto en caja" (FEATURES.md P1/P2)
**Uses:** Existing JasperReports infrastructure (STACK.md — no new report engine needed)

### Phase Ordering Rationale

- Gold engine first because it's the only currently-blocking correctness issue (PROJECT.md constraint) and every downstream monto (amortización, printed contract, caja report) inherits its accuracy.
- Beneficiario is deliberately slotted early despite being unrelated in logic, purely because it's small/isolated and delays accumulate more bad data the longer it's deferred.
- Silver runs in parallel with gold (different code path, independent fields) rather than sequentially, since architecture research confirms no shared dependency.
- Sanción verification comes after the money-calculation phases because it depends on `montoPrestamo` already being correct (sanción is a percentage of that base).
- Corte de caja is sequenced after the calculation phases specifically so its "trustworthy totals" claim is actually true when shipped, not something to re-verify later.
- PDF/reimpresión is last because it's a pure consumer of every other phase's output — printing wrong numbers first and fixing them later would create rework and customer-facing confusion.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (Motor de Oro):** Needs the actual COCAE 24-cell screenshot data before implementation is complete — currently a business-data gap, not a code gap (PROJECT.md: "pendiente de captura adicional"). Also needs the rounding-contract capture (per-step vs round-once) from real printouts.
- **Phase 3 (Motor de Plata):** No confirmed COCAE formula exists yet for how `ley` and `precioGramoPlata` combine (linear? own margin factor analogous to `porcAumento`?) — flag for `/gsd:research-phase` or a direct client verification pass before finalizing the formula.
- **Phase 4 (Sanción):** Weekly-rounding rule (ceil vs pro-rata) unverified against real overdue COCAE refrendo captures.
- **Phase 6 (PDF):** `contrato.jrxml` field bindings/layout were not inspected in this research pass (binary/XML report definition) — needs a direct read before implementation.

Phases with standard patterns (skip research-phase):
- **Phase 2 (Beneficiario Obligatorio):** Well-documented Liquibase backfill pattern, standard Bean Validation — no research needed.
- **Phase 5 (Corte de Caja):** Standard `GROUP BY` aggregation query over an existing, complete schema — no research needed.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All recommended technologies are already proven in this exact codebase; no new third-party libraries evaluated, only application of existing JDK/Spring/MariaDB patterns. Verified against official docs (MariaDB, MySQL InnoDB, Oracle JavaDoc). |
| Features | MEDIUM | Regulatory findings (CONDUSEF/PROFECO/rate-cap status) are MEDIUM-HIGH, cross-checked across multiple official sources. Exact numeric parity with legacy COCAE (gold cell values, silver leyes 925 vs 720/725, sanción rounding rule) remains LOW until confirmed against captured screenshots — this is the single largest unresolved gap across all four research files. |
| Architecture | HIGH | Based on direct inspection of current source code (not stale planning docs), which revealed the codebase is significantly further along than PROJECT.md suggested. Concrete class/method-level integration points identified for every feature. |
| Pitfalls | HIGH (codebase-grounded) / MEDIUM (general concurrency/migration practice) | Every pitfall traced to specific files/lines in the current implementation, not hypothetical scenarios. General MariaDB concurrency and Liquibase migration guidance is MEDIUM (verified via current external sources but not project-specific). |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **COCAE exact numeric values (gold 24-cell table, silver leyes 925/720 vs 925/725 discrepancy, sanción week-rounding rule):** All four research files converge on this as the critical unresolved gap. Cannot be closed by further research — requires capturing 15-20 real COCAE contract printouts/screenshots spanning kilatajes, hechuras, pesos, and at least one overdue refrendo, per the project's own stated verification method. Roadmap should treat this as a blocking input to Phase 1/3/4, not a task the engineering team can resolve alone.
- **`contrato.jrxml` field bindings:** Not inspected in this research pass (binary/XML report definition). A direct read is needed before Phase 6 implementation — low risk, quick to resolve.
- **Whether the gold table is global or varies per plazo:** Explicitly flagged as unresolved in PROJECT.md ("pendiente de captura adicional") — affects the Liquibase import design for Phase 1 (single global changeset vs per-plazo variants).
- **Multi-sucursal folio strategy:** Deliberately deferred to v2+; current global PK-derived folio is a known, documented, acceptable limitation only while `sucursalId` stays hardcoded to `1`. Roadmap should not build this now but should note the `TODO` in code as a tracked limitation.

## Sources

### Primary (HIGH confidence)
- Codebase inspection (this repo) — `prestamil-backend/src/main/java/com/ignis/prestamil/service/{PlazoService,ContratoService,MovimientoContratoService}.java`, model classes, Liquibase changesets 001-011, `pom.xml`
- MariaDB Server Documentation — AUTO_INCREMENT Constraints — https://mariadb.com/docs/server/architecture/server-constraints/auto_increment-constraints
- MySQL 8.0 Reference Manual — InnoDB Auto-Increment Handling — https://dev.mysql.com/doc/refman/8.0/en/innodb-auto-increment-handling.html
- Oracle JavaDoc — BigDecimal / RoundingMode — https://docs.oracle.com/javase/8/docs/api/java/math/BigDecimal.html
- Liquibase — addNotNullConstraint reference — https://docs.liquibase.com/change-types/add-not-null-constraint.html

### Secondary (MEDIUM confidence)
- CONDUSEF — información general casas de empeño — https://condusef.gob.mx/?idc=899&idcat=1&p=contenido
- PROFECO — "Sí, nos empeñamos en informarte" — https://www.gob.mx/profeco/documentos/si-nos-empenamos-en-informarte-prestamos-con-garantia-prendaria
- DOF — NOM-179-SCFI-2016 — https://www.dof.gob.mx/nota_detalle.php?codigo=5493105&fecha=08/08/2017 (full text not machine-parseable; recommend manual legal review)
- Ley Federal de Protección al Consumidor, Art. 65 Bis — https://leyes-mx.com/ley_federal_de_proteccion_al_consumidor/65%20Bis.htm
- MariaDB Sequences documentation — https://mariadb.com/kb/en/sequences/
- Baeldung — BigDecimal and BigInteger in Java — https://www.baeldung.com/java-bigdecimal-biginteger

### Tertiary (LOW confidence)
- Silver ley "725" vs "720" figure in PROJECT.md — needs re-verification against a real COCAE screenshot (may be a transcription error)
- SIL Gobernación — iniciativa Art. 65 Bis 7 (2025, tope 36% anual, not yet enacted) — https://sil.gobernacion.gob.mx (proposed legislation, not current law)
- Reposición fee benchmarks from competitor sources (Monte de Piedad tiered fixed fee, Oaxaca 2% model) — informal/secondary sources, directional only

---
*Research completed: 2026-07-02*
*Ready for roadmap: yes*
