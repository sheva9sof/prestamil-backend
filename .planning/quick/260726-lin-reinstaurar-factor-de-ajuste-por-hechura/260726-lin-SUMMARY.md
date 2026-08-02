---
phase: quick-260726-lin
plan: 01
subsystem: motor-de-oro
tags: [backend, frontend, liquibase, bigdecimal, tdd, oro, configuracion]
status: INCOMPLETE — Task 5 (human verification) PENDING
dependency_graph:
  requires:
    - PrecioOro entity + PrecioOroRequest/Response (Phase 4.1)
    - OroTablaPrestamoService.toOroCeldaResponse (Phase 4.1)
    - PlazoService.recalcularRegistros (Phase 4)
  provides:
    - PrecioOro.factorFundir/factorNormal/factorEspecial (columns + fields)
    - PrecioOro.factorDeHechura(precio, hechura) shared static helper
    - Factor applied in OroTablaPrestamoService.toOroCeldaResponse (reference screen)
    - Factor applied in PlazoService.recalcularRegistros (PlazoHechuraAlhaja.precioBase — real loan amount)
    - 3 editable factor inputs in Configuracion del Oro screen
  affects:
    - prestamil-backend/PrecioOroRequest.java (new nullable fields)
    - prestamil-backend/PrecioOroResponse.java (new fields)
    - prestamil-backend/PlazoService.java (recalcularRegistros signature + all 3 callers)
    - prestamil-backend/OroTablaPrestamoService.java (toOroCeldaResponse signature)
    - prestamil-frontend/oro-config.service.ts (actualizarPrecioGramo signature change)
tech_stack:
  added: []
  patterns:
    - "BigDecimal shared static helper on the owning entity (PrecioOro.factorDeHechura) instead of duplicating per-service"
    - "Upsert-before-recalculate ordering to avoid using stale factors in the same operation (recalcularTodasLasTablas)"
    - "TDD: RED (failing factor-dependent tests) before GREEN (multiply-by-factor implementation)"
key_files:
  created:
    - prestamil-backend/src/main/resources/db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/OroTablaPrestamoServiceTest.java
    - .planning/quick/260726-lin-reinstaurar-factor-de-ajuste-por-hechura/deferred-items.md
  modified:
    - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
    - prestamil-backend/src/main/java/com/ignis/prestamil/model/PrecioOro.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/request/PrecioOroRequest.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/response/PrecioOroResponse.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/OroTablaPrestamoService.java
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
    - prestamil-frontend/src/app/prestamil/core/models/oro-config.model.ts
    - prestamil-frontend/src/app/prestamil/core/services/oro-config.service.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.html
    - .planning/phases/04.1-configuracion-del-oro-admin-ui-para-tabla-de-24-celdas/04.1-CONTEXT.md
    - .planning/REQUIREMENTS.md
    - .planning/PROJECT.md
decisions:
  - "D-C revisada (2026-07-26): el usuario confirmo explicitamente que el factor de hechura debe afectar el monto real del prestamo, no solo la pantalla de referencia — por eso se propaga a PlazoService.recalcularRegistros, no solo a OroTablaPrestamoService"
  - "Seed 100.0000 (neutro) en el changeset 017, nunca los defaults viejos 90/100/110 — al desplegar ningun monto vigente cambia"
  - "factorDeHechura vive como helper estatico en PrecioOro (entidad duena de los 3 campos) para que los dos motores de calculo compartan exactamente la misma logica, en vez de duplicarla"
  - "En recalcularTodasLasTablas el upsert de los 3 factores del request ocurre ANTES de invocar recalcularRegistros, para que el mismo recalculo ya refleje los factores nuevos (trampa de orden documentada en el plan)"
metrics:
  duration: "~70 minutes (Tasks 1-4)"
  completed_date: "2026-07-26 (Tasks 1-4 only; Task 5 pending)"
  tasks_completed: "4/5 (Task 5 is a blocking human-verify checkpoint, not yet resolved)"
  files_created: 3
  files_modified: 14
---

# Phase quick-260726-lin Plan 01: Reinstaurar factor de ajuste por hechura Summary

**One-liner:** Reinstauró factorFundir/Normal/Especial en `PrecioOro` (seed neutro 100.0000, configurable por sucursal) y lo aplicó como multiplicador adicional en los DOS motores de cálculo — `OroTablaPrestamoService.toOroCeldaResponse` (pantalla de referencia) y `PlazoService.recalcularRegistros` (`PlazoHechuraAlhaja.precioBase`, monto real de contratos nuevos) — vía un helper estático compartido `PrecioOro.factorDeHechura(...)`.

**STATUS: PLAN INCOMPLETE.** Tasks 1-4 están terminadas, commiteadas y verificadas (todas las suites en verde). **Task 5 (verificación humana, checkpoint bloqueante) sigue PENDIENTE** — no se ha recibido la señal de reanudación del usuario. No marcar este plan como completo hasta que Task 5 se resuelva.

---

## What Was Built

### Task 1 — Backend: esquema, entidad, DTOs y helper compartido (commit `b3b59b1`, prestamil-backend)

- Changeset Liquibase `017-restaurar-factores-hechura-precio-oro.sql`: agrega `factor_fundir`/`factor_normal`/`factor_especial` (`DECIMAL(7,4) NOT NULL DEFAULT 100.0000`) a `precio_oro`, con rollback explícito. Registrado en `db.changelog-master.xml` después de `016-*`. El changeset `013-drop-factores-hechura-precio-oro.sql` no se tocó (D-F).
- `PrecioOro.java`: 3 campos nuevos (`precision=7, scale=4`, inicializados por String), constante `FACTOR_NEUTRO = new BigDecimal("100.0000")`, y el helper estático compartido:
  ```java
  public static BigDecimal factorDeHechura(PrecioOro precio, String hechura)
  ```
  tolerante a `precio == null`, `hechura == null`, factor `null` o hechura desconocida — siempre devuelve `FACTOR_NEUTRO` en esos casos, nunca lanza.
- `PrecioOroRequest.java`: 3 campos `BigDecimal` nullable con `@DecimalMin("0.0")` — `null` conserva el valor vigente.
- `PrecioOroResponse.java`: 3 campos correspondientes.
- `PlazoService.toPrecioOroResponse`: mapea los 3 campos nuevos.
- Verificado: `./mvnw -q test` en verde con los 35 tests previos **sin ninguna aserción numérica modificada** — plumbing puro.

### Task 2 — Backend: aplicar el factor en los dos motores de cálculo, TDD (commit `a9872f7`, prestamil-backend)

- **RED primero:** se escribieron `OroTablaPrestamoServiceTest` (nuevo, 5 casos) y 5 casos nuevos en `PlazoServiceTest` (los 3 originales quedaron intactos), confirmados en falla antes de tocar la implementación.
- **GREEN:**
  - `OroTablaPrestamoService.toOroCeldaResponse(celda, precio)`: firma cambiada para recibir `PrecioOro` directamente (en vez de `precioGramo24k`/`baseKilataje` sueltos); ambos llamadores (`getTabla`, `actualizarCelda`) actualizados. `precioPrestamo` ahora multiplica por `PrecioOro.factorDeHechura(precio, hechura)/100`, con escala intermedia 10 HALF_UP y `setScale(4, HALF_UP)` final (D-06).
  - `PlazoService.recalcularRegistros(...)`: nuevo parámetro final `PrecioOro precio` (nullable, nunca releído de BD dentro del método). `precioBase` ahora multiplica por el mismo `factorDeHechura`. La línea de `precioPrestamo = precioBase * (1 + porcAumento/100)` **no se tocó** (D-10).
  - Los 3 llamadores de `recalcularRegistros` (`actualizarTodosPrecios`, `recalcularPrecioBasePorTablaOro`, `recalcularTodasLasTablas`) pasan el `precio` que ya tenían en scope.
  - **Trampa de orden resuelta** en `recalcularTodasLasTablas`: el upsert null-tolerante de los 3 factores del request (`if (request.getFactorX() != null) precio.setFactorX(...)`) se insertó inmediatamente después de cargar/crear `precio`, **antes** de invocar `recalcularRegistros` — verificado con `grep`: `setFactorFundir`/`setFactorNormal`/`setFactorEspecial` aparecen antes de la llamada a `recalcularRegistros` dentro de ese método.
- Verificado: `OroTablaPrestamoServiceTest` (5/5) + `PlazoServiceTest` (8/8, 3 originales + 5 nuevos) + suite completa `./mvnw -q test` (todos en verde, sin fallos).
- `grep -n "porcAumento" PlazoService.java` no tiene coincidencias nuevas relacionadas al factor — D-10 intacto.
- `git diff --stat` confirma que `OroTablaPrestamo.java`, `OroTablaPrestamoId.java`, `OroTablaPrestamoRepository.java`, `PlazoHechuraAlhaja.java` y changesets previos a 017 no cambiaron (D-B/D-F).

### Task 3 — Frontend: 3 inputs de factor por hechura (commit `91af41d`, prestamil-frontend)

- `oro-config.model.ts`: `PrecioGramoResponse`/`PrecioGramoRequest` exponen `factorFundir`/`factorNormal`/`factorEspecial` (request: opcionales).
- `oro-config.service.ts`: `actualizarPrecioGramo` ahora recibe el `PrecioGramoRequest` completo en vez de solo `precioGramoBase`; único consumidor (`configuracion-oro.component.ts`) actualizado.
- `configuracion-oro.component.ts`: nuevo estado `factores: { F, N, E }`, poblado en `cargarPrecioGramo()` (con fallback a 100 si aún no hay `PrecioOro` configurado o si la petición falla); `guardarPrecioGramo()` valida que los 3 factores sean `>= 0` y no nulos antes de enviar, y envía el objeto completo con el mismo botón "Guardar y recalcular" (que ya dispara `cargarTabla()` para refrescar "Precio Prestamo (referencia)").
- `configuracion-oro.component.html`: 3 inputs `type="number"` (`factorFundirInput`/`factorNormalInput`/`factorEspecialInput`) junto al input de precio del gramo, con nota explicativa.
- Verificado: `ng build` compila y bundlea sin errores de tipo (dos "✔ Browser application bundle generation complete." antes de fallar por presupuesto SCSS **preexistente y no relacionado**, ver Deviations). `ng lint`: 108 errores, **idéntico conteo antes y después de este cambio** (verificado con `git stash`/`git stash pop` contra el baseline `559e6a9`).

### Task 4 — Documentación de planning (commit `c67e186`, repo raíz `.planning`)

- `04.1-CONTEXT.md`: bloque `**D-17 REVERTIDA PARCIALMENTE (2026-07-26, quick task 260726-lin):**` agregado inmediatamente después de D-17 (D-16/D-17 originales **no se borraron** — registro histórico conservado). Marcador `(revertido parcialmente — ver D-17 REVERTIDA)` agregado en `<domain>`.
- `REQUIREMENTS.md`: ORO-08 marcado `Superseded (por ORO-09, 2026-07-26)`; nuevo requisito `ORO-09` agregado como `Complete`, origen `quick 260726-lin`. Traceability table actualizada.
- `PROJECT.md`: línea de `Validated` de Phase 4.1 actualizada, bullet nuevo en `## Context` sobre el factor de ajuste (afirma explícitamente que SÍ afecta el monto real, sin dejar la pregunta abierta), fila nueva en `## Key Decisions`.
- Verificado con los 3 `grep` del plan (`REVERTIDA`, `ORO-09`, `260726-lin`) — todos encuentran coincidencia.

---

## Pre-existing Uncommitted Work Found and Committed First

Al iniciar la ejecución, `prestamil-backend` y `prestamil-frontend` tenían cambios sin commitear de una sesión anterior (no relacionados con este quick task — companion del bugfix de normalización de hechura documentado en STATE.md del 2026-07-26): el changeset `016-reparar-tabla-prestamo-oro.sql` + su registro en `db.changelog-master.xml`, y dos ajustes defensivos en `configuracion-oro.component.html/.ts` (mensaje de tabla vacía, `trackByCelda` corregido). Se verificó que la suite de tests pasaba (35/35) con ese estado como baseline, y se commitearon por separado **antes** de iniciar Task 1, para que los commits de este plan quedaran limpios y scoped:

- `827f439` (prestamil-backend): `fix(04.1): completar celdas faltantes de oro_tabla_prestamo por sucursal`
- `559e6a9` (prestamil-frontend): `fix(04.1): mensaje de tabla vacia y trackBy correcto en Configuracion del Oro`

Ambos commits se crearon exitosamente desde esta sesión (sin problema de aislamiento de worktree).

---

## Commits

| Repo | Hash | Mensaje |
|---|---|---|
| prestamil-backend | `827f439` | fix(04.1): completar celdas faltantes de oro_tabla_prestamo por sucursal (pre-existing, committed first) |
| prestamil-frontend | `559e6a9` | fix(04.1): mensaje de tabla vacia y trackBy correcto en Configuracion del Oro (pre-existing, committed first) |
| prestamil-backend | `b3b59b1` | feat(quick-260726-lin): reinstaurar esquema/DTOs/helper del factor de hechura (ORO-09) — **Task 1** |
| prestamil-backend | `a9872f7` | feat(quick-260726-lin): aplicar factor de hechura en los dos motores de calculo (ORO-09) — **Task 2** |
| prestamil-frontend | `91af41d` | feat(quick-260726-lin): 3 inputs de factor por hechura en Configuracion del Oro (ORO-09) — **Task 3** |
| .planning (raíz) | `c67e186` | docs(quick-260726-lin): documentar reversion parcial de D-16/D-17 (ORO-09) — **Task 4** |

All commits succeeded directly from this session — no worktree-isolation fallback commands were needed this time (contrast with the Phase 04.1 note in STATE.md, where a different agent session hit that isolation issue).

---

## Deviations from Plan

### Auto-fixed Issues

None — Tasks 1-4 were implemented per the plan's `<action>`/`<behavior>` specs without needing bug fixes, missing-functionality additions, or blocking-issue fixes beyond what the plan already specified.

### Out-of-scope items logged (not fixed)

See `.planning/quick/260726-lin-reinstaurar-factor-de-ajuste-por-hechura/deferred-items.md`:

1. **Frontend build fails on pre-existing SCSS budget errors** in `empresas.component.scss` and `sucursal.component.scss` — neither touched by this task. Verified pre-existing via `git stash`/rebuild against baseline `559e6a9` (identical failure, same exit code 1).
2. **Frontend lint has 108 pre-existing errors** across unrelated files. Verified identical count with and without this task's Task 3 diff via `git stash`.

### Note on illustrative numbers in the plan's `<behavior>` section

The plan's Task 2 `<behavior>` section included one illustrative expected value that does not match the arithmetic of the specified formula/rounding contract: `getTabla_factorFundir90_reducePrecioPrestamoDeLaCeldaFundir` stated an expected `precioPrestamo` of `"986.7825"` for `1750.0000 x 0.6267 x 0.90`, but that product is actually `987.0525` (verified by hand and confirmed by the passing test). The test as implemented asserts the mathematically correct value (`987.0525`); this does not change any implementation logic — it is purely a corrected test fixture value, and the actual "no-regression" test (factor neutro 100) and the `PlazoServiceTest` factor tests (which reused a clean round-number scenario, `precioGramoBase="2400.0000"`) all match the plan's stated values exactly.

---

## Known Stubs

None. No hardcoded empty values, placeholder text, or unwired data sources were introduced by this plan.

---

## What Remains (Task 5 — blocking checkpoint, NOT YET RESOLVED)

Task 5 is a `checkpoint:human-verify` gate. Per the plan, execution must pause here and present the following to the user; **do not** treat this plan as complete, do not update `ROADMAP.md` (quick tasks don't touch it per the execution constraints), and do not run milestone-completion steps until the user responds with "aprobado" or reports discrepancies.

**What was built:** Factor de ajuste por hechura reinstaurado y configurable: 3 columnas en `precio_oro` (seed 100.0000), 3 inputs editables junto al precio del gramo, y el factor aplicado en los DOS motores de cálculo — el Precio Prestamo de referencia de la pantalla Configuración del Oro **y** el `precioBase` de `PlazoHechuraAlhaja`, que es el monto real que se ofrece en un contrato nuevo.

**How to verify** (steps to present to the user):
1. Aplicar el changeset 017 (`./mvnw liquibase:update -Pdev` o arrancando el backend) y confirmar que `DESCRIBE precio_oro` muestra `factor_fundir`/`factor_normal`/`factor_especial` en 100.0000.
2. Arrancar backend + frontend y abrir Configuración / Configuración del Oro.
3. **No-regresión:** con los tres factores en 100, anotar el "Precio Prestamo (referencia)" de 21K Normal, y en Plazos y Periodos anotar el precio base/prestamo de esa misma celda. Ambos deben ser exactamente los mismos valores que mostraban antes de este cambio.
4. Cambiar "Factor Fundir (%)" a 90 y presionar "Guardar y recalcular". La pestaña **Fundir** debe bajar ~10% en todas sus filas; **Normal** y **Especial** no deben cambiar.
5. Recargar la página (F5): los 3 factores deben conservar el valor guardado.
6. **Propagación al monto real (lo que cambió en esta revisión):**
   a. Abrir **Plazos y Periodos**: las filas de hechura **Fundir** deben haber bajado ~10% respecto al paso 3; Normal y Especial sin cambios.
   b. Para la misma celda kilataje/hechura, el precio base de Plazos y Periodos debe **coincidir** con el "Precio Prestamo (referencia)" de Configuración del Oro.
   c. Iniciar un contrato **NUEVO** con una prenda de oro de hechura Fundir: el monto ofrecido debe reflejar el factor 90 (~10% menor).
   d. Abrir un contrato **YA EXISTENTE (VIGENTE)** de hechura Fundir: su monto **NO** debe haber cambiado — los contratos emitidos quedan snapshoteados (D-09).
7. **Aportar los valores reales de COCAE** para las 3 hechuras (captura de la ventana legacy) si están disponibles — el plan NO inventó valores por diseño (D-E). Al capturarlos, recordar que ahora sí mueven montos de préstamo reales.

**Resume signal:** "aprobado", o descripción de las diferencias encontradas (incluyendo los valores reales de COCAE si se capturaron).

---

## Self-Check (Tasks 1-4 only)

Files verified to exist:
- prestamil-backend/src/main/resources/db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql: FOUND
- prestamil-backend/src/test/java/com/ignis/prestamil/service/OroTablaPrestamoServiceTest.java: FOUND
- .planning/quick/260726-lin-reinstaurar-factor-de-ajuste-por-hechura/deferred-items.md: FOUND

Commits verified to exist (`git log --oneline --all | grep <hash>`):
- b3b59b1: FOUND (prestamil-backend)
- a9872f7: FOUND (prestamil-backend)
- 91af41d: FOUND (prestamil-frontend)
- c67e186: FOUND (.planning root)
- 827f439: FOUND (prestamil-backend)
- 559e6a9: FOUND (prestamil-frontend)

Backend: 13/13 new/extended tests PASS + 35 previous tests PASS (48 total), `BUILD SUCCESS`.
Frontend: TypeScript compilation/bundling succeeds (pre-existing, unrelated SCSS budget failure documented in deferred-items.md); lint error count unchanged from baseline (108).

## Self-Check: PASSED (for Tasks 1-4; Task 5 still pending — plan not complete)
