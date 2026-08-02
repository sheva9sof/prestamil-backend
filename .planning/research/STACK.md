# Stack Research

**Domain:** Financial calculation engine + contract lifecycle for a pawnshop (casa de empeño) management system — legacy-parity money math, on-the-fly amortization, concurrency-safe sequential IDs, date-based penalty calculation.
**Researched:** 2026-07-02
**Confidence:** HIGH (all core mechanisms are JDK/Spring/MariaDB stdlib features already present and proven in this codebase — verified against official docs, not new third-party libraries)

> **Supersedes** the FULLTEXT-search-scoped `STACK.md` written 2026-05-14 for a prior milestone. That work is complete and validated (see `PROJECT.md` → Validated); this file now covers the v1.1 "Motor de Cálculo Real y Ciclo de Vida del Contrato" milestone.

## Headline Finding

**This milestone needs zero new runtime dependencies.** Every capability required — (a) exact-decimal money math, (b) on-the-fly amortization, (c) concurrency-safe folio numbers, (d) date-based penalty calc — is already covered by `java.math.BigDecimal`, `java.time`, Spring Data JPA, and MariaDB InnoDB `AUTO_INCREMENT`, all of which the codebase already uses correctly in `PlazoService` and `ContratoService`. The work here is disciplined **application of existing patterns**, not stack expansion. Adding a money/decimal library, a scheduler, or a DB sequence object would be over-engineering for a single-branch, single-server, low-throughput pawnshop system.

## Recommended Stack

### Core Technologies (already in the project — apply, don't add)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `java.math.BigDecimal` | JDK 21 (bundled) | Exact-decimal money/percentage math | Already the project convention (`RoundingMode.HALF_UP`, explicit `.setScale()`) in `PlazoService`/`ContratoService`. No float/double anywhere — correct baseline for legacy-parity work. |
| `java.time` (JSR-310: `LocalDate`, `LocalDateTime`, `ChronoUnit`) | JDK 21 (bundled) | Date math for vencimientos, refrendos, sanciones | Already used for `fechaVencimiento` calc in `ContratoService.crearContrato`. `ChronoUnit.DAYS.between(...)` is the correct primitive for "days/weeks late" — no need for `java.util.Date`/Joda-Time. |
| Liquibase SQL-formatted changesets | 4.27.0 (pom-pinned) | Import the COCAE `%Prestamo` lookup table (8 kilates × 3 hechuras) as literal seed data | Codebase already does exactly this pattern in `002-initial-data.sql`, `009-clientes-prueba.sql`, and `011-oro-sancion-plata.sql` (INSERT statements in a numbered changeset). Matches the project constraint "todos los cambios de schema deben ir en un changeset Liquibase numerado." |
| MariaDB InnoDB `AUTO_INCREMENT` | Any MariaDB 10.x/11.x (server-version agnostic) | Concurrency-safe consecutive folio numbers | InnoDB's auto-increment counter allocation is atomic under all lock modes (`innodb_autoinc_lock_mode` 0/1/2) — guarantees uniqueness and monotonic increase across concurrently executing inserts. The existing `ContratoService.crearContrato()` pattern (save → derive folio from generated `id` → save again, both within the same `@Transactional` method) is already correct and safe. |

### Supporting Libraries

None needed. Do not add anything for this milestone — see "What NOT to Use" below.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `spring-boot-starter-test` + Mockito (already in pom) | Unit tests asserting exact `BigDecimal` outputs against COCAE reference values | Write parity tests as `assertEquals(new BigDecimal("1234.56"), resultado)` — never `assertEquals(double, double, delta)`. Feed real COCAE screenshots as literal `String`-constructed `BigDecimal` test fixtures. |
| H2 (test scope, already in pom) | In-memory DB for repository/service integration tests | Verify `DECIMAL(p,s)` column scale round-trips correctly through Hibernate — H2's DECIMAL emulation is close enough for scale/precision assertions, but run the final parity suite against real MariaDB before shipping. |

## Integration Guidance by Question

### (a) Financial precision matching COCAE exactly

**Pattern already correct, one addition needed:**

1. **Keep `BigDecimal` + explicit `RoundingMode.HALF_UP` + explicit `.setScale(n, ...)`** on every arithmetic step, exactly as `PlazoService.recalcularRegistros()` and `ContratoService.calcularPrestamoMaximo()` already do. Never let an operation return a `BigDecimal` with unspecified/inherited scale into a persisted field.
2. **Always construct `BigDecimal` literals from `String`**, not `double` (e.g. `new BigDecimal("100")`, already the convention via the `CIEN` constant in `PlazoService`). A `double`-sourced `BigDecimal` silently introduces binary floating-point error before any rounding logic runs.
3. **The COCAE `%Prestamo` table (8 kilates × 3 hechuras = 24 cells) must be imported as literal data, not a derived formula** — this is already flagged as a confirmed decision in `PROJECT.md`. Import it via a new numbered Liquibase SQL changeset (`012-...sql`), following the exact convention of `011-oro-sancion-plata.sql`. Store it either as new columns on `plazo_hechura_alhaja` (it already has a `tabla_prestamo_id` column anticipating multiple import tables) or a new `tabla_prestamo_cocae` reference table keyed by `(kilataje, hechura)` — decide based on whether the table is global or varies per plazo once the pending screenshot capture (noted in `PROJECT.md`) resolves that question.
4. **Match COCAE's rounding points, not just its final totals.** Research confirms that legacy-parity work requires replicating *where* the legacy system rounds (after each multiply/divide) rather than the mathematically "cleaner" round-once-at-the-end approach — a single extra intermediate rounding step is a common source of one-centavo discrepancies. Once COCAE screenshots showing intermediate calculated values (not just final totals) are available, encode each rounding step as an explicit `.setScale()` call with an inline comment explaining which COCAE step it mirrors — this matches the existing codebase convention of "inline step comments in service methods explaining intent" and gives future maintainers a reason not to "simplify" it into round-once math.
5. **Keep Java `BigDecimal` scale aligned with MariaDB `DECIMAL(p,s)` column scale.** The codebase already has a working convention: prices `DECIMAL(12,4)`, percentages `DECIMAL(7,4)`/`DECIMAL(9,4)`, money amounts `DECIMAL(18,2)`. Any new COCAE-derived columns should follow the same precision tiers rather than inventing new ones — one recent changeset (`011-9`) already had to widen `porc_aumento` from `DECIMAL(5,4)` to `DECIMAL(7,4)` because the original scale was too narrow; verify COCAE's actual precision (some legacy pawn systems store percentages to 2 decimals only) before picking a scale, to avoid a repeat.

### (b) On-the-fly amortization without persisting intermediate rows

**Already implemented correctly** in `ContratoService.calcularAmortizacion()` — it computes an in-memory `List<VencimientoResponse>` from `fechaApertura` + `plazo.getDiasPorPeriodo()` × period index, entirely inside a `@Transactional(readOnly = true)` method, with no entity/table backing the intermediate rows. Continue this pattern for any new schedule-shaped output (e.g., the offline reference PDF in target feature #7):

- `VencimientoResponse` (or its equivalent) must remain a plain DTO, never a `@Entity`. Do not create a `vencimiento`/`amortizacion` table — that would violate the explicit architecture decision in `PROJECT.md` ("solo guarda fecha inicial + vencimiento final").
- For the sanción calculation (2% semanal), compute directly from `ChronoUnit.DAYS.between(fechaVencimiento, LocalDate.now())`, then derive elapsed weeks. **Open question for the business, not a stack question:** whether partial weeks round up (ceiling) or truncate — `MovimientoContrato` already has a `semanas_vencidas` INT column (added in changeset `011-7`) suggesting whole-week granularity was anticipated; confirm the rounding rule with Jorge before hardcoding `Math.ceil` vs integer division.

### (c) Sequential/consecutive folio generation safe under concurrent writes

**The current implementation is already concurrency-safe — keep it, do not replace it.**

`ContratoService.crearContrato()` saves the `Contrato` once to obtain the InnoDB-generated `id`, formats `folio = "CTR-%06d"` from that `id`, then saves again — both writes inside the same `@Transactional` method/connection. Because MariaDB's `AUTO_INCREMENT` counter allocation is atomic regardless of `innodb_autoinc_lock_mode` (verified against MariaDB/InnoDB documentation), two concurrent cashiers creating contracts simultaneously can never receive the same `id`/folio — the DB engine itself serializes counter allocation, not the Java code.

Two acceptable, understood trade-offs of this pattern (no action needed, just be aware):
- **Gaps, not duplicates, on rollback.** If the transaction fails after the first save, the consumed `id` is not reused (InnoDB never rolls back the counter) — the next successful contract will have a folio with a gap. This is normal AUTO_INCREMENT behavior and matches how most legacy pawn systems already behave (voided/failed tickets leave gaps) — do not attempt to "fill" gaps.
- **Two writes per contract instead of one.** This is a minor efficiency cost, acceptable at pawnshop transaction volumes (single branch, one cashier per turno). Not worth optimizing away by pre-computing the folio (e.g., via a separate counter table) — that would introduce exactly the concurrency risk this pattern already avoids "for free."

**Do not introduce:** a dedicated `SELECT MAX(folio)+1` counter query (classic race condition — two concurrent reads can get the same MAX before either INSERT commits), a hand-rolled counter table with `SELECT ... FOR UPDATE` (adds lock contention and a new failure mode for zero benefit over what InnoDB already guarantees), or MariaDB `SEQUENCE` objects (available MariaDB 10.3+, but purpose-built for multi-table/gapless-adjacent scenarios this system doesn't have — the folio is derived from a single table's own PK).

### (d) Java/Spring patterns for late-fee/penalty calculation on date-based schedules

- Pure `java.time` arithmetic in the service layer — no framework needed. `ChronoUnit.DAYS.between(fechaVencimiento, LocalDate.now())` → derive semanas vencidas → `sancion = montoBase.multiply(porcSancionSemanal).divide(CIEN, ...).multiply(BigDecimal.valueOf(semanasVencidas))`, mirroring the exact `BigDecimal` idiom already used in `PlazoService.calcularAvaluoContrato()`.
- Keep the penalty calculation **read-time/on-write-time (when a refrendo is registered), not batch-computed.** This matches the milestone's "calculate on the fly" philosophy for `MovimientoContrato` — the `sancion`/`semanas_vencidas` columns already added in changeset `011-7` are for storing the *result* of a penalty applied to a specific `REFRENDO` movement, not a running/scheduled recalculation.
- **No `@Scheduled` job is required for this milestone.** A nightly batch to auto-transition `Contrato.estatus` from `VIGENTE` to `VENCIDO` is a plausible future need, but it's not in the Active requirements list in `PROJECT.md` — `estatus` can continue to be read/derived at query time (`getContratosVencidos()` already filters by stored `estatus`, but nothing here blocks computing "is this contract late" on the fly from `fechaVencimiento` vs. `LocalDate.now()` instead of relying on a batch-updated column). If a scheduled job becomes necessary later, `spring-boot-starter` already provides `@Scheduled`/`@EnableScheduling` — no new dependency, just a config class analogous to the existing `*Config` classes.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| `java.math.BigDecimal` (stdlib) | `javax.money` / JSR-354 (Moneta reference impl) | Only if the system needed multi-currency support with currency-aware arithmetic. Prestamil is single-currency (MXN) — the `Money` abstraction adds ceremony (unit conversion, `MonetaryAmount` boxing) with zero benefit here. |
| Folio derived from `AUTO_INCREMENT` PK (existing pattern) | MariaDB `SEQUENCE` object (10.3+) | If folios ever needed to be reserved *before* the row exists (e.g., pre-printing a folio on a physical ticket before the contract record is finalized), or if folios needed to span multiple tables. Neither applies today. |
| On-the-fly amortization computed in `@Transactional(readOnly = true)` service methods | Persisted `vencimiento`/`amortizacion` table | If the business ever needs to *audit* what the schedule looked like at a specific point in time independent of current `plazo`/`fechaVencimiento` values (e.g., after a plazo's `dias_por_periodo` config changes retroactively). Not a stated requirement; would also contradict the explicit "no persistir fechas intermedias" decision. |
| Liquibase numbered SQL changeset with literal `INSERT`s for the COCAE table | Liquibase `<loadData>` CSV import (XML/YAML changeset) | If the COCAE table were large (hundreds+ rows) or externally maintained as a spreadsheet handed off by ops. At 24 cells, a plain SQL `INSERT` changeset is simpler, keeps the existing all-SQL changelog convention, and is easier to review as a diff. |
| Rounding-per-step to mirror COCAE | Round-once-at-the-end (theoretically "more correct" math) | Only for genuinely new calculations that have no legacy system to match (e.g., a brand-new fee type invented for this milestone with no COCAE precedent) — for those, round once at the point of persistence/display. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| `double`/`float` anywhere in money or percentage math | Binary floating point cannot represent decimal fractions exactly (e.g., `0.1 + 0.2 != 0.3`) — guaranteed to produce off-by-fractions-of-a-centavo mismatches against COCAE. | `BigDecimal` constructed from `String` literals, exactly as the codebase already does. |
| `javax.money`/JSR-354 or any "Money" value-object library | Adds an abstraction layer (currency-aware types, unit conversion) the project doesn't need — single currency, already-working `BigDecimal` convention. Pure over-engineering for this milestone's scope. | Continue plain `BigDecimal` + explicit `RoundingMode`/`scale`. |
| Quartz Scheduler | Heavyweight (persistent job store, misfire policies, clustering config) for a need this milestone doesn't actually have (no scheduled job required — see (d) above). | If a future milestone needs a nightly status-transition job, Spring's built-in `@Scheduled` (already available via `spring-boot-starter`, zero new deps) is sufficient at this scale. |
| MariaDB `SEQUENCE` objects or a dedicated folio counter table | Solves a concurrency problem InnoDB `AUTO_INCREMENT` already solves for free; adds a second point of failure/lock contention. | Keep deriving the folio from the `Contrato.id` auto-increment PK, as already implemented. |
| A persisted amortization/vencimientos table | Directly contradicts the confirmed architecture decision ("solo guarda fecha inicial + vencimiento final") and duplicates data that's cheap to recompute from `fechaApertura` + `plazo`. | Keep `calcularAmortizacion()` as a read-time, non-persisted DTO projection. |
| Joda-Time or any pre-JSR-310 date library | `java.time` (JSR-310) has been the JDK standard since Java 8; Joda-Time is in maintenance-only mode and its author explicitly recommends migrating away from it. | `java.time.LocalDate` / `ChronoUnit`, already the codebase convention. |
| Hibernate Envers or a generic audit-trail framework for tracking sanción/refrendo history | `movimiento_contrato` already serves as an explicit, purpose-built audit trail for financial movements (`REFRENDO`/`FINIQUITO`/`ABONO` + `sancion`/`abono_capital`/`semanas_vencidas` columns) — a generic framework would duplicate this with more complexity. | Keep appending rows to `movimiento_contrato`, following the existing `MovimientoContratoService` pattern. |

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-------------------|-------|
| `java.math.BigDecimal` / `java.time` | Java 21 (project-pinned) | Both are core JDK APIs with no version risk — stable since Java 8 (`java.time`) and Java 1.1 (`BigDecimal`, with `RoundingMode` enum since Java 5). |
| Liquibase SQL-formatted changesets | `liquibase-core` (Boot-managed) + `liquibase-maven-plugin` 4.27.0 (pom-pinned) | No version concern — the project already has 11 numbered changesets using this exact format; changeset 012+ follows the same syntax with zero migration risk. |
| MariaDB `AUTO_INCREMENT` behavior described above | Any MariaDB server version deployed (10.x/11.x, driver `mariadb-java-client` Boot-managed) | Atomic auto-increment allocation under InnoDB has been stable since `innodb_autoinc_lock_mode` was introduced (MySQL 5.1.22 / inherited by MariaDB) — not a version-sensitive feature for this project's needs. |

## Sources

- MariaDB Server Documentation — [AUTO_INCREMENT Constraints](https://mariadb.com/docs/server/architecture/server-constraints/auto_increment-constraints) — verified atomic/thread-safe allocation semantics and gap-on-rollback behavior. Confidence: HIGH (official docs).
- MySQL 8.0 Reference Manual — [17.6.1.6 AUTO_INCREMENT Handling in InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-auto-increment-handling.html) — InnoDB lock-mode mechanics MariaDB inherits. Confidence: HIGH (official docs, InnoDB engine shared lineage).
- Baeldung — [BigDecimal and BigInteger in Java](https://www.baeldung.com/java-bigdecimal-biginteger) — `setScale`/`RoundingMode` usage guidance, round-at-the-boundary principle. Confidence: MEDIUM (reputable secondary source, cross-checked against Oracle JavaDoc).
- Oracle — [BigDecimal (Java Platform SE 8)](https://docs.oracle.com/javase/8/docs/api/java/math/BigDecimal.html) / [RoundingMode](https://docs.oracle.com/javase/8/docs/api/java/math/RoundingMode.html) — authoritative API contract for scale/rounding semantics. Confidence: HIGH (official JDK docs).
- Codebase inspection (this repo) — `prestamil-backend/src/main/java/com/ignis/prestamil/service/{PlazoService,ContratoService}.java`, `prestamil-backend/src/main/resources/db/changelog/changes/{002,007,009,011}-*.sql`, `prestamil-backend/pom.xml`, `.planning/codebase/AVALUOS.md`, `.planning/PROJECT.md` — verified existing conventions (BigDecimal/RoundingMode usage, Liquibase changeset format, folio generation logic, already-existing sanción/plata/precio_oro schema from changeset 011). Confidence: HIGH (primary source, direct read).

---
*Stack research for: Prestamil v1.1 — Motor de Cálculo Real y Ciclo de Vida del Contrato*
*Researched: 2026-07-02*
