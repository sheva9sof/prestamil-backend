---
phase: quick-260516-mns
plan: 01
subsystem: plazos
tags: [backend, frontend, multi-sucursal, alhajas, liquibase, jpa, angular]
dependency_graph:
  requires: []
  provides:
    - PlazoHechuraAlhaja entity + repository + DTOs + mapper
    - PlazoParametroId with sucursalId (3-field composite key)
    - 5 new REST endpoints for parametros and alhajas with sucursalId
    - plazo.service.ts (9 HTTP methods)
    - plazos-periodos two-panel layout with tabs
    - parametros-prestamo read-only view
  affects:
    - prestamil-backend/PlazoService.java (signature change, new methods)
    - prestamil-backend/PlazoController.java (5 new endpoints)
    - prestamil-backend/PlazoParametroId.java (breaking: added sucursalId)
    - prestamil-frontend/plazos-periodos.component.ts (full refactor)
    - prestamil-frontend/parametros-prestamo.component.ts (no longer empty)
tech_stack:
  added:
    - NgbNavModule (Angular tab component via ng-bootstrap)
    - forkJoin (RxJS parallel HTTP calls in ParametrosPrestamoComponent)
  patterns:
    - @EmbeddedId for PlazoHechuraAlhaja (vs @IdClass for PlazoParametro)
    - Manual @Component mapper (PlazoHechuraAlhajaMapper) — no MapStruct
    - inject() style for Angular services (no constructor injection)
    - upsert pattern for guardarParametro (findById then save)
    - BigDecimal arithmetic with explicit RoundingMode.HALF_UP scale=4
key_files:
  created:
    - prestamil-backend/src/main/resources/db/changelog/changes/006-plazos-sucursal.sql
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhajaId.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoHechuraAlhajaRequest.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoHechuraAlhajaResponse.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoHechuraAlhajaMapper.java
    - prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
    - prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts
  modified:
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametroId.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoParametroRepository.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
    - prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.html
    - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
    - .planning/codebase/ARCHITECTURE.md
    - .planning/codebase/CONCERNS.md
    - .planning/codebase/STRUCTURE.md
decisions:
  - "@IdClass retained for PlazoParametro (existing pattern) vs @EmbeddedId used for new PlazoHechuraAlhaja — consistency within entity, not across entities"
  - "ADD COLUMN IF NOT EXISTS removed from migration 006 — MariaDB version in use does not support this syntax; Liquibase changeset tracking prevents re-execution"
  - "sucursalId hardcoded to 1 in frontend — future task should read sucursalId from active session/TurnoService"
  - "PlazoHechuraAlhaja uses tablaPrestamoId as a non-PK column (existing DB design preserved)"
metrics:
  duration: 16 minutes
  completed_date: 2026-05-16
  tasks_completed: 9
  files_created: 10
  files_modified: 14
---

# Phase quick-260516-mns Plan 01: PlazoHechuraAlhaja + Multi-Sucursal Plazos Summary

**One-liner:** Agregó soporte multi-sucursal a plazo_parametro y plazo_hechura_alhaja con fórmula de precio de oro (precioBaseOro/24)*kilataje*31.1035, 5 nuevos endpoints REST y componentes Angular completos.

---

## What Was Built

### Backend

**Migración Liquibase 006** (`006-plazos-sucursal.sql`):
- `plazo_parametro`: agrega `sucursal_id INT NOT NULL DEFAULT 1`, reconstruye PK como `(plazo_id, tipo_prenda_id, sucursal_id)`, mantiene FK a `plazo_prenda` y agrega FK a `sucursal`.
- `plazo_hechura_alhaja`: agrega `sucursal_id INT NOT NULL DEFAULT 1`, agrega FK a `sucursal`, crea índice compuesto `idx_pha_sucursal`.

**Nuevas entidades JPA**:
- `PlazoHechuraAlhajaId`: `@Embeddable` con 4 campos (`idPlazo`, `sucursalId`, `kilataje`, `hechura`), equals/hashCode explícitos.
- `PlazoHechuraAlhaja`: `@Entity` con `@EmbeddedId`, campos `tablaPrestamoId`, `precioBase`, `porcAumento`, `precioPrestamo`.

**Modificaciones a entidades existentes**:
- `PlazoParametroId`: agrega `sucursalId`, actualiza equals/hashCode, agrega constructor de 3 args. Los dos constructores anteriores se conservan.
- `PlazoParametro`: agrega `@Id sucursal_id` (patrón `@IdClass` existente conservado).

**DTOs y mapper**:
- `PlazoHechuraAlhajaRequest`: `@NotNull`, `@NotBlank`, `@Pattern([FNE])`, `@DecimalMin`.
- `PlazoHechuraAlhajaResponse`: incluye `hechuraDescripcion` ("Fina"/"Normal"/"Especial").
- `PlazoHechuraAlhajaMapper`: `@Component` manual, métodos `toResponse()` y `toEntity()`.
- `PlazoParametroMapper`: agrega `actualizarDesdeRequest()` para upsert.
- `PlazoParametroResponse`: agrega campo `sucursalId`.

**5 nuevos endpoints REST**:

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/api/plazos/{id}/parametros?sucursalId=1` | Lista parámetros por sucursal |
| `PUT` | `/api/plazos/{id}/parametros/{tipoPrendaId}?sucursalId=1` | Upsert parámetro |
| `GET` | `/api/plazos/{id}/alhajas?sucursalId=1` | Lista tabla de hechuras |
| `PUT` | `/api/plazos/{id}/alhajas/{kilataje}/{hechura}?sucursalId=1` | Actualiza precio base individual |
| `PUT` | `/api/plazos/{id}/alhajas/precio-oro?sucursalId=1` | Recalcula todos los precios |

**Endpoint existente actualizado**:
- `GET /api/plazos/{idPlazo}/parametros/{idTipoPrenda}` ahora acepta `?sucursalId=1` y lanza `ResourceNotFoundException` en vez de devolver null.

**Bug fix (PlazoService.getParametrosPlazo)**:
- Antes: devolvía `null` cuando no encontraba el registro.
- Ahora: lanza `ResourceNotFoundException` con mensaje descriptivo. La firma cambió de `(Long, Integer)` a `(Long, Integer, Integer)`.

**Fórmula del oro implementada**:
```
precioBase = (precioBaseOro / 24) * kilataje * 31.1035
precioPrestamo = precioBase * (1 + porcAumento)
```

### Frontend

**`plazo.model.ts`**: Interfaces `PlazoRequest/Response`, `PlazoParametroRequest/Response`, `PlazoHechuraAlhajaRequest/Response`, `TipoPrendaResponse`.

**`plazo.service.ts`**: 9 métodos HTTP cubriendo todos los endpoints. `inject()` style, `sucursalId` default=1.

**`plazos-periodos.component`** (refactorizado):
- Layout dos paneles: izquierdo (lista) + derecho (detalle con tabs).
- Tab "Parámetros": tabla solo lectura de `PlazoParametroResponse`.
- Tab "Alhajas": campo de precio base oro + botón recalcular masivo + edición inline por fila.
- Usa `inject(PlazoService)` (no constructor).

**`parametros-prestamo.component`** (implementado desde cero):
- Vista solo lectura agrupada por plazo activo usando `forkJoin`.
- Card por plazo con tabla de parámetros.
- `isLoading`/`errorMessage` pattern, standalone component.

---

## What Changed (Breaking Changes)

| Elemento | Cambio | Impacto |
|----------|--------|---------|
| `PlazoParametroId` | Agrega `sucursalId` a clave compuesta | **Breaking**: rows existentes migradas a `sucursal_id=1` por migration |
| `PlazoService.getParametrosPlazo()` | Firma: `(Long, Integer)` → `(Long, Integer, Integer)` | **Breaking**: cualquier llamada existente debe pasar `sucursalId` |
| `PlazoController.getParametrosPlazo()` | Acepta `?sucursalId=1` (default=1) | Retrocompatible vía valor por defecto |

---

## How to Verify Locally

```bash
# 1. Backend compila y tests pasan
cd prestamil-backend && ./mvnw.cmd clean test

# 2. Frontend compila
cd prestamil-frontend && npx ng build --configuration=development

# 3. Endpoints REST (con Postman o curl, asumiendo sesión activa):

# Listar parámetros de plazo 1 para sucursal 1:
# GET http://localhost:8080/api/plazos/1/parametros?sucursalId=1

# Tabla de alhajas de plazo 1 para sucursal 1:
# GET http://localhost:8080/api/plazos/1/alhajas?sucursalId=1

# Recalcular precio de una hechura (body: {"precioBase": 1250.00}):
# PUT http://localhost:8080/api/plazos/1/alhajas/14/F?sucursalId=1

# Recalcular todos con precio de oro (body: {"precioBaseOro": 62000.00}):
# PUT http://localhost:8080/api/plazos/1/alhajas/precio-oro?sucursalId=1
```

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ResourceNotFoundException constructor accepts only one String**
- **Found during:** Task 4 (PlazoService implementation)
- **Issue:** `new ResourceNotFoundException("entity", "detail")` — el constructor solo acepta un String.
- **Fix:** Convertido a mensaje único: `new ResourceNotFoundException("entity no encontrada: " + detail)`.
- **Files modified:** `PlazoService.java`
- **Commit:** fb671fb

**2. [Rule 1 - Bug] ADD COLUMN IF NOT EXISTS not supported by MariaDB version in use**
- **Found during:** Task 6 (backend tests — contextLoads failure)
- **Issue:** El servidor MariaDB del entorno arroja error de sintaxis en `ADD COLUMN IF NOT EXISTS`.
- **Fix:** Removido `IF NOT EXISTS` de los changesets 006-1 y 006-4. Liquibase previene re-ejecución por su propio mecanismo de checksums.
- **Files modified:** `006-plazos-sucursal.sql`
- **Commit:** a221487

**3. [Rule 2 - Missing functionality] actualizarDesdeRequest() missing in PlazoParametroMapper**
- **Found during:** Task 4 (PlazoService.guardarParametro needs it)
- **Fix:** Agregado método `actualizarDesdeRequest(PlazoParametro, PlazoParametroRequest)` al mapper.
- **Files modified:** `PlazoParametroMapper.java`
- **Commit:** f4e83c8

---

## Known Follow-ups

- `sucursalId` está hardcodeado a `1` en el frontend (`plazos-periodos`, `parametros-prestamo`). Futuro: leer de `TurnoService.currentTurno$` o sesión activa del usuario.
- `plazo_hechura_alhaja` no tiene PK definida en el schema original — la migración agrega `sucursal_id` pero no define una PK compuesta. Esto puede causar problemas con JPA si hay datos duplicados de (`id_plazo`, `sucursal_id`, `kilataje`, `hechura`). Futuro: agregar `ALTER TABLE plazo_hechura_alhaja ADD PRIMARY KEY (id_plazo, sucursal_id, kilataje, hechura)`.
- El filtro por tipo de prenda del panel izquierdo en `plazos-periodos` fue simplificado (solo lista todos los plazos, no filtra por tipo de prenda como antes). Si se necesita el filtro: agregar endpoint `GET /api/prendas/tipos` al `PlazoService` o a un nuevo `PrendaService` en el frontend.

## Self-Check: PASSED

All required files exist:
- 006-plazos-sucursal.sql: FOUND
- PlazoHechuraAlhaja.java: FOUND
- PlazoHechuraAlhajaId.java: FOUND
- PlazoHechuraAlhajaRepository.java: FOUND
- PlazoHechuraAlhajaRequest.java: FOUND
- PlazoHechuraAlhajaResponse.java: FOUND
- PlazoHechuraAlhajaMapper.java: FOUND
- plazo.model.ts: FOUND
- plazo.service.ts: FOUND
- parametros-prestamo.component.html: FOUND

Backend: 26/26 tests PASS, BUILD SUCCESS
Frontend: BUILD SUCCESS (no TypeScript errors)
