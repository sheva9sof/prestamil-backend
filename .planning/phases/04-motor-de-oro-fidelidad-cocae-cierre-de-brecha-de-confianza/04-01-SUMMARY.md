---
phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza
plan: 01
subsystem: database
tags: [liquibase, jpa, mariadb, cocae, oro]

# Dependency graph
requires:
  - phase: 02-plazohechuraalhaja
    provides: PlazoHechuraAlhaja/PlazoHechuraAlhajaId pattern replicated exactly for OroTablaPrestamo
provides:
  - "oro_tabla_prestamo table (Liquibase changeset 012) with 24 confirmed COCAE cells (8 kilates x 3 hechuras) for sucursal 1"
  - "OroTablaPrestamo/OroTablaPrestamoId JPA entity + composite key, read-only"
  - "OroTablaPrestamoRepository.findByIdSucursalId, ready for consumption by PlazoService"
affects: [04-02-plazo-service-recalculo]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Read-only global-per-sucursal lookup table pattern (no mapper/DTO/controller) for reference data consumed only by a service, not exposed via API"

key-files:
  created:
    - prestamil-backend/src/main/resources/db/changelog/changes/012-oro-tabla-prestamo-cocae.sql
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/OroTablaPrestamoId.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/OroTablaPrestamo.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/repository/OroTablaPrestamoRepository.java
  modified:
    - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml

key-decisions:
  - "No mapper/DTO/controller created for OroTablaPrestamo — read-only reference data in this phase, populated only via Liquibase (D-02/D-03)"

patterns-established:
  - "OroTablaPrestamoId/OroTablaPrestamo/OroTablaPrestamoRepository replicate PlazoHechuraAlhajaId/PlazoHechuraAlhaja/PlazoHechuraAlhajaRepository exactly (3-field composite key instead of 4, since this table is global per sucursal and not per plazo)"

requirements-completed: [ORO-01]

# Metrics
duration: 15min
completed: 2026-07-03
---

# Phase 04 Plan 01: Tabla COCAE de %Prestamo Summary

**Changeset Liquibase 012 crea `oro_tabla_prestamo` (24 celdas reales de COCAE, 8 kilates x 3 hechuras) global por sucursal, mas la entidad JPA `OroTablaPrestamo`/`OroTablaPrestamoId` y su repositorio de solo lectura, listos para que `PlazoService` (plan 04-02) reemplace el calculo por 3 factores globales por el lookup real.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 2 completed
- **Files modified:** 5 (4 created, 1 modified)

## Accomplishments
- Persistida la tabla real de COCAE (24 celdas: 8 kilates x 3 hechuras) como datos vía Liquibase, no como código Java hardcodeado
- Tabla confirmada global por sucursal (no varía por plazo), consistente con la decisión D-01
- Entidad JPA de solo lectura y repositorio listos para ser inyectados en `PlazoService` en el plan 04-02, sin cambiar ningún comportamiento existente todavía

## Task Commits

Each task was committed atomically (in the `prestamil-backend` repo, branch `manu`):

1. **Task 1: Changeset Liquibase 012 — tabla oro_tabla_prestamo + 24 filas COCAE** - `6f73d95` (feat)
2. **Task 2: Entidad JPA OroTablaPrestamo + repositorio de solo lectura** - `2a6f5d5` (feat)

_Note: `prestamil-backend` is a separately-versioned nested git repository (its own history, branch `manu`), distinct from the outer GSD-tracked repository that holds `.planning/` and `CLAUDE.md`. Both task commits live in that nested repo._

## Files Created/Modified
- `prestamil-backend/src/main/resources/db/changelog/changes/012-oro-tabla-prestamo-cocae.sql` - Changeset Liquibase: crea `oro_tabla_prestamo` (PK sucursal_id+kilataje+hechura, FK a sucursal) e inserta las 24 filas confirmadas de COCAE v3.80 para sucursal 1
- `prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml` - Registra el include del changeset 012 después del 011, sin alterar el orden ni contenido de changesets previos
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/OroTablaPrestamoId.java` - Clave compuesta `@Embeddable` (sucursalId, kilataje, hechura) con equals/hashCode propios
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/OroTablaPrestamo.java` - Entidad JPA `@Entity`/`@Table(name = "oro_tabla_prestamo")` con `@EmbeddedId`, porcPrestamo, actualizadoEn
- `prestamil-backend/src/main/java/com/ignis/prestamil/repository/OroTablaPrestamoRepository.java` - Repositorio extendiendo `BaseRepository`, expone `findByIdSucursalId(Integer sucursalId)`

## Decisions Made
- Ninguna decisión nueva — se siguió exactamente el patrón de `PlazoHechuraAlhaja`/`PlazoHechuraAlhajaId`/`PlazoHechuraAlhajaRepository` como especificó el plan, reduciendo la clave compuesta de 4 a 3 campos (sin `idPlazo`, ya que la tabla es global por sucursal)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Repo topology note (not a deviation, informational):** `prestamil-backend` and `prestamil-frontend` are separate nested git repositories on disk at `C:/Users/Emm-a/Documents/GitHub/prestamil/`, not tracked by the outer repo that holds `.planning/`. The parallel-executor worktree for this agent (`C:\Users\Emm-a\.claude\worktrees\agent-ac558d69f678b801a\...`) only contains the outer repo's tracked files (`.planning/`, `CLAUDE.md`); it does not have its own copy of `prestamil-backend`. Source-code edits and their commits were therefore made directly in the shared on-disk `prestamil-backend` nested repo (branch `manu`), while this SUMMARY and STATE/ROADMAP updates are made in the worktree's outer repo. This means other parallel agents executing plans against `prestamil-backend` share the same working tree — task-level file staging (never `git add -A`) was used throughout to avoid capturing unrelated in-flight changes from other agents.

## User Setup Required

None - no external service configuration required. Note: the Liquibase changeset will apply automatically on next backend startup (or via `mvn liquibase:update` per the project's dev/qa profile setup) — no manual DB step needed beyond the normal app boot / migration run.

## Next Phase Readiness
- `OroTablaPrestamoRepository.findByIdSucursalId` is ready to be injected into `PlazoService.recalcularRegistros` in plan 04-02 to replace the 3-global-factor calculation with the real 24-cell COCAE lookup.
- No behavior changed yet — existing `precio_oro` factor-based calculation is untouched and still active until 04-02 wires the new table in.

---
*Phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza*
*Completed: 2026-07-03*

## Self-Check: PASSED

All 5 created/modified files found on disk; both task commits (6f73d95, 2a6f5d5) found in prestamil-backend git history (branch manu).
