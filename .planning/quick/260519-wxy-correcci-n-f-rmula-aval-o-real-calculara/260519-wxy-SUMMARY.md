---
phase: quick-260519-wxy
plan: 01
subsystem: plazos-parametros
tags: [formula-fix, dto-rename, frontend-preview, avaluo, plazo-service]
dependency_graph:
  requires: []
  provides: [calcularAvaluoContrato, porcIncrementoAvaluo-renaming, avaluo-live-preview]
  affects: [PlazoService, PlazoParametroRequest, PlazoParametroResponse, PlazoParametroMapper, plazo.model.ts, plazos-periodos.component]
tech_stack:
  added: []
  patterns: [DTO-rename-without-schema-change, mapper-field-translation, live-preview-helper]
key_files:
  created: []
  modified:
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
    - prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
decisions:
  - "DTO rename sin migración DB: porcIncrementoAvaluo en la frontera HTTP/UI; porcPrestamoSAvaluoReal sigue siendo el campo de entidad y columna DB"
  - "Mapper manual traduce porcIncrementoAvaluo (DTO) ↔ porcPrestamoSAvaluoReal (entidad) en ambas direcciones"
  - "Preview en vivo usa la misma fórmula monto×(1+porc/100) que calcularAvaluoContrato para consistencia visual"
  - "Tooltip implementado con atributo HTML nativo 'title' para evitar importar ng-bootstrap tooltip directive"
metrics:
  duration: ~15min
  completed: 2026-05-20
  tasks_completed: 2
  tasks_total: 3
  files_modified: 7
---

# Phase quick-260519-wxy Plan 01: Corrección Fórmula Avalúo Real Summary

**One-liner:** Fórmula avalúo corregida a monto×(1+porc/100) con helper calcularAvaluoContrato en PlazoService; DTOs y frontend renombrados a porcIncrementoAvaluo; preview en vivo con tooltip educativo en Tab 1 de plazos-periodos.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Backend — calcularAvaluoContrato + rename DTOs/mapper | `0e3a753` (backend) | PlazoService.java, PlazoParametroRequest.java, PlazoParametroResponse.java, PlazoParametroMapper.java |
| 2 | Frontend — rename model/component/template + preview en vivo | `2b26b52` (frontend) | plazo.model.ts, plazos-periodos.component.ts, plazos-periodos.component.html |
| 3 | Verificación humana | PENDING — awaiting checkpoint | — |

## What Was Built

### Backend (Task 1)

- **`calcularAvaluoContrato(BigDecimal montoPrestamo, PlazoParametro parametro)`** added to `PlazoService` as public method. Formula: `monto × (1 + porcPrestamoSAvaluoReal / 100)`. Returns `montoPrestamo` unchanged when `usaAvaluoReal=false` or porcentaje is zero/null.
- **`PlazoParametroRequest`**: Field renamed `porcPrestamoSAvaluoReal` → `porcIncrementoAvaluo`. Lombok regenerates `getPorcIncrementoAvaluo()` / `setPorcIncrementoAvaluo()`.
- **`PlazoParametroResponse`**: Field renamed identically.
- **`PlazoParametroMapper`** (manual, not MapStruct): Three call sites updated:
  - `toPlazoParametroResponse`: calls `response.setPorcIncrementoAvaluo(entity.getPorcPrestamoSAvaluoReal())`
  - `actualizarDesdeRequest`: reads `request.getPorcIncrementoAvaluo()`, writes to `entity.setPorcPrestamoSAvaluoReal(...)`
  - `toPlazoParametro`: reads `request.getPorcIncrementoAvaluo()`, writes to `entity.setPorcPrestamoSAvaluoReal(...)`
- DB column `porc_prestamo_s_avaluo_real` and JPA entity field `porcPrestamoSAvaluoReal` unchanged. No Liquibase migration required.
- `mvn -q -DskipTests compile` passes.

### Frontend (Task 2)

- **`plazo.model.ts`**: `PlazoParametroRequest.porcPrestamoSAvaluoReal` → `porcIncrementoAvaluo`, `PlazoParametroResponse.porcPrestamoSAvaluoReal` → `porcIncrementoAvaluo`.
- **`plazos-periodos.component.ts`**:
  - Default form object updated: `porcPrestamoSAvaluoReal: 0` → `porcIncrementoAvaluo: 0`
  - Added `readonly PREVIEW_PRESTAMO = 1000` class property
  - Added `avaluoPreview(tipoPrendaId: number): { prestamo: number; avaluo: number }` helper using the same formula
- **`plazos-periodos.component.html`** (Tab 1 Parámetros block):
  - Label: "% Préstamo s/Avalúo Real" → "% Incremento sobre Avalúo"
  - `ngModel` binding: `porcPrestamoSAvaluoReal` → `porcIncrementoAvaluo`
  - Added `title` attribute with educational tooltip text (HTML native, no ng-bootstrap import required)
  - Added `<small>` preview: "Si el préstamo es $1,000.00 → avalúo en contrato: $X.XX" using `avaluoPreview(t.id)` + `number:'1.2-2'` pipe
- `npx ng build --configuration development` passes (SCSS deprecation warnings are pre-existing from Bootstrap — out of scope).

## Deviations from Plan

None — plan executed exactly as written. All four field update points in the mapper matched the plan spec. No additional files were needed.

## Known Stubs

None. The formula is fully wired: the preview in the template calls `avaluoPreview()` which uses `form.porcIncrementoAvaluo` from the live `parametrosForm` binding.

## Verification Results

### Automated (completed)
- `mvn -q -DskipTests compile` — PASSED (no errors)
- `npx ng build --configuration development` — PASSED (warnings are pre-existing SCSS deprecations from Bootstrap, unrelated to this task)
- `grep porcIncrementoAvaluo prestamil-frontend/src/` — 5 occurrences (2 in model, 2 in .ts, 1 in .html) — PASSED
- `grep porcPrestamoSAvaluoReal prestamil-frontend/src/` — 0 occurrences — PASSED
- `grep porcPrestamoSAvaluoReal request/ response/` (backend) — 0 occurrences — PASSED
- `grep calcularAvaluoContrato PlazoService.java` — 1 occurrence at line 312 — PASSED

### Human verification (Task 3 — pending)
Expected behavior to verify:
- Preview shows $1,000.00 → $1,500.00 when porc=50 and usaAvaluoReal=true
- Preview shows $1,000.00 → $1,000.00 when porc=0 or usaAvaluoReal=false
- Tooltip appears on hover over the input field
- Saving and reloading persists the value (payload uses porcIncrementoAvaluo)

## Self-Check: PASSED

Files modified exist:
- prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java — FOUND (contains calcularAvaluoContrato)
- prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java — FOUND (contains porcIncrementoAvaluo)
- prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java — FOUND (contains porcIncrementoAvaluo)
- prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java — FOUND (translated correctly)
- prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts — FOUND (porcIncrementoAvaluo in both interfaces)
- prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts — FOUND (helper + constant)
- prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html — FOUND (binding + preview)

Commits exist:
- Backend: `0e3a753` — FOUND
- Frontend: `2b26b52` — FOUND
