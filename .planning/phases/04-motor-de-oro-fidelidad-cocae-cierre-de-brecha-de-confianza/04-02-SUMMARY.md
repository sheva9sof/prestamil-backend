---
phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza
plan: 02
subsystem: backend
tags: [java, spring-boot, jpa, plazo, oro, cocae, mockito]

# Dependency graph
requires:
  - phase: 04-01
    provides: OroTablaPrestamo entity, OroTablaPrestamoId embeddable key, OroTablaPrestamoRepository (findByIdSucursalId) — the 24-cell COCAE %Prestamo table
provides:
  - "PlazoService.recalcularRegistros derives precioBase per cell (kilataje x hechura) from oro_tabla_prestamo instead of 3 global factors (Fundir/Normal/Especial)"
  - "porcAumento per cell preserved unchanged during recalculation (ORO-02)"
  - "PlazoServiceTest.java — first unit test coverage for PlazoService, exact compareTo parity against real COCAE captures"
affects: [04-04, 06-motor-de-plata, 07-sancion-verificacion]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Map<String,BigDecimal> keyed by 'kilataje-hechura' string to resolve per-cell %Prestamo from a flat repository list, avoiding N+1 lookups per row"
    - "compareTo-only BigDecimal assertions in tests (D-06), never assertEquals/.equals()"

key-files:
  created:
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
  modified:
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java

key-decisions:
  - "factorFundir/Normal/Especial remain persisted on PrecioOro (D-02, still used by the 'Precio del Oro' screen) but no longer participate in precioBase calculation for plazo_hechura_alhaja"
  - "recalcularRegistros throws ResourceNotFoundException (not a silent default) when a kilataje/hechura cell is missing from oro_tabla_prestamo for the sucursal — surfaces data gaps instead of masking them with a wrong price"

patterns-established:
  - "Per-cell lookup table built once per recalculation call (Collectors.toMap) rather than repeated repository queries per row"

requirements-completed: [ORO-01, ORO-02, ORO-04]

# Metrics
duration: ~15min
completed: 2026-07-03
---

# Phase 04 Plan 02: Motor de Oro — recalculo desde oro_tabla_prestamo Summary

**PlazoService.recalcularRegistros ahora deriva precioBase de la tabla real de 24 celdas COCAE (oro_tabla_prestamo) en vez de 3 factores globales, con paridad exacta verificada contra capturas reales (21K/Normal → precioBase 1065.4748, precioPrestamo 1172.0223)**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-03T19:33:15Z
- **Tasks:** 2/2
- **Files modified:** 2 (1 modified, 1 created)

## Accomplishments
- Closed ORO-01: the gold engine now reads the real 24-cell COCAE %Prestamo table (`oro_tabla_prestamo`, built in 04-01) instead of computing precioBase from 3 global hechura factors (Fundir/Normal/Especial)
- Closed ORO-02: `porcAumento` stored per plazo/cell is never touched by the recalculation — verified explicitly in the test suite
- Added first unit test coverage for `PlazoService` (previously untested), asserting exact `compareTo` parity against real COCAE captures per D-06 (never `assertEquals`/`.equals()` on `BigDecimal`)
- `actualizarTodosPrecios` and `recalcularTodasLasTablas` both updated to call the new 4-arg `recalcularRegistros(registros, precioGramoBase, baseKilataje, sucursalId)`; `recalcularTodasLasTablas` still persists factorFundir/Normal/Especial on `PrecioOro` for the "Precio del Oro" screen (D-02), unaffected by this change
- Removed dead code: `factorPorHechura` private method, no longer referenced after the formula rewrite

## Task Commits

Each task was committed atomically (nested repo `prestamil-backend`, branch `manu`):

1. **Task 1: Reescribir recalcularRegistros usando oro_tabla_prestamo (ORO-01, ORO-02)** - `57cd94d` (feat)
2. **Task 2: PlazoServiceTest — paridad exacta contra capturas reales de COCAE** - `1783211` (test)

**Plan metadata:** pending (this commit, docs)

## Files Created/Modified
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java` - `recalcularRegistros` rewritten to look up %Prestamo per (kilataje,hechura) cell from `OroTablaPrestamoRepository.findByIdSucursalId`; constructor gains `oroTablaPrestamoRepository` as last param; `actualizarTodosPrecios`/`recalcularTodasLasTablas` call sites updated; `factorPorHechura` removed
- `prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java` - new file, 4 Mockito unit tests covering exact-parity calculation, global-factor irrelevance, and two `ResourceNotFoundException` regression paths

## Decisions Made
- Kept `factorFundir`/`factorNormal`/`factorEspecial` persistence in `recalcularTodasLasTablas` untouched (still feeds the "Precio del Oro" screen per D-02) while removing their use in the alhaja-price formula — this was explicit in the plan interfaces section and required no re-derivation
- Threw `ResourceNotFoundException` (not a fallback price) when a cell is missing from `oro_tabla_prestamo`, matching the plan's `<behavior>` spec and the project's fail-fast convention for missing lookup data (`BaseService`/`ResourceNotFoundException` pattern already used across services)

## Deviations from Plan

None - plan executed exactly as written. `PlazoService.java` and the new `PlazoServiceTest.java` match the interfaces, formulas, and acceptance criteria specified in `04-02-PLAN.md` task by task (constructor param order preserved, `recalcularRegistros` signature has exactly 4 params, `factorPorHechura` removed, `setFactorFundir` still present in `recalcularTodasLasTablas`).

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- ORO-01/ORO-02 closed for the `PlazoService` side of the gold engine; `mvn compile` succeeds for the whole backend module, confirming no regression for other consumers of `PlazoService` (e.g., `ContratoService` from 04-03, which only depends on public methods not touched here)
- ORO-04 partially covered (PlazoService side); remaining ORO-04 verification work (if any, e.g. ContratoService-level integration parity) is out of this plan's scope
- No blockers identified for subsequent phases (05-beneficiario, 06-plata, 07-sancion)

---
*Phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza*
*Completed: 2026-07-03*

## Self-Check: PASSED

- FOUND: prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
- FOUND: prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
- FOUND: .planning/phases/04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza/04-02-SUMMARY.md
- FOUND commit: 57cd94d (Task 1)
- FOUND commit: 1783211 (Task 2)
