---
phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza
plan: 03
subsystem: api
tags: [spring-boot, jpa, contratos, oro, avaluo, mockito, junit5]

# Dependency graph
requires:
  - phase: 02-plazohechuraalhaja
    provides: "PlazoHechuraAlhaja entity + repository (precioPrestamo por plazo/sucursal/kilataje/hechura)"
provides:
  - "ContratoService.calcularAvaluoRealAlhaja() — recalculo server-side del avaluo real de partidas ALHAJA a partir de PlazoHechuraAlhaja.precioPrestamo x pesoGramos"
  - "Rechazo explicito de kilataje 24K ('Oro de 24K no es prendable') y kilatajes no soportados ('Kilataje no soportado: {valor}')"
  - "buildPartida/calcularPrestamoMaximo ya no confian en pr.getAvaluoReal() del cliente para partidas ALHAJA — cierre de brecha de confianza D-07"
  - "ContratoServiceTest.java — primera cobertura de pruebas de ContratoService, con 4 pruebas de regresion"
affects: [04-01-motor-de-oro-tabla-prestamo, 04-02, 06-motor-de-plata, contrato-controller]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Avaluo real de ALHAJA se calcula 100% server-side; pr.getAvaluoReal() del request se ignora para ese tipo de prenda (se mantiene solo para tipos de libre avaluo)"
    - "Tests de ContratoService via metodo publico crearContrato(), capturando el Contrato persistido con ArgumentCaptor porque buildPartida/calcularAvaluoRealAlhaja son privados"

key-files:
  created:
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/ContratoServiceTest.java
  modified:
    - prestamil-backend/src/main/java/com/ignis/prestamil/service/ContratoService.java

key-decisions:
  - "calcularPrestamoMaximo cambio de firma para recibir BigDecimal avaluoReal ya calculado en vez de PartidaContratoRequest, para no reintroducir la lectura de pr.getAvaluoReal()"
  - "esAlhaja() compara TipoPrenda.getTipo() por texto (case-insensitive) en vez de asumir id=1 hardcodeado, para mayor robustez"

patterns-established:
  - "Helpers privados de calculo server-side se documentan con Javadoc en espanol incluyendo @throws para las excepciones de negocio (D-04/D-05)"

requirements-completed: [ORO-03, ORO-04]

# Metrics
duration: 20min
completed: 2026-07-03
---

# Phase 04 Plan 03: Cierre de brecha de confianza en avaluo de ALHAJA Summary

**ContratoService ahora recalcula el avaluoReal de partidas ALHAJA server-side desde PlazoHechuraAlhaja (precioPrestamo x pesoGramos), ignorando el valor enviado por el cliente, y rechaza kilataje 24K / kilatajes no soportados con mensajes de negocio claros.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-03
- **Tasks:** 2/2
- **Files modified:** 2 (1 modificado, 1 creado)

## Accomplishments
- `calcularAvaluoRealAlhaja()` nuevo: recalcula el avalúo real de partidas ALHAJA a partir de `PlazoHechuraAlhaja.precioPrestamo x pesoGramos` (escala 2, HALF_UP), consultando `PlazoHechuraAlhajaRepository.findById(...)` con la celda plazo/sucursal/kilataje/hechura.
- Rechazo explícito de kilataje 24K (`BadRequestException("Oro de 24K no es prendable")`) ANTES de tocar el repositorio, y de kilatajes fuera de la tabla COCAE de 8 valores (`"Kilataje no soportado: {valor}"`).
- `buildPartida()` y `calcularPrestamoMaximo()` ya no leen `pr.getAvaluoReal()` para partidas ALHAJA — el avalúo real usado para el techo de préstamo y para persistir la partida es siempre el calculado por el servidor. Para tipos NO-ALHAJA (Varios/electrónicos) el comportamiento anterior se conserva sin cambios.
- `ContratoServiceTest.java` nuevo (primer test de esta clase) con 4 pruebas que demuestran: (1) un `avaluoReal` spoofed de 999999.00 no se persiste — se persiste 12000.00 calculado por el servidor; (2) kilataje 24K se rechaza con el mensaje exacto; (3) kilataje 16 (no soportado) se rechaza con el mensaje exacto; (4) un `montoPrestamo` que "coincide" con el avalúo spoofed del cliente sigue siendo rechazado porque el techo real (12000.00) lo excede.

## Task Commits

Ambos commits se hicieron en el repositorio anidado `prestamil-backend` (git nested repo, rama `manu`), no en el repo raíz de `.planning`:

1. **Task 1: calcularAvaluoRealAlhaja server-side + cierre de brecha en buildPartida/calcularPrestamoMaximo** - `1c5059f` (feat)
2. **Task 2: ContratoServiceTest — regresion de brecha de confianza y rechazos D-04/D-05** - `5622f04` (test)

**Plan metadata:** (este commit, en el repo raíz `.planning`)

## Files Created/Modified
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/ContratoService.java` - Nuevo campo/parámetro `PlazoHechuraAlhajaRepository`, constante `KILATAJES_COCAE`, métodos `calcularAvaluoRealAlhaja()` y `esAlhaja()`; `buildPartida()` calcula `avaluoReal` antes de `calcularPrestamoMaximo()`; `calcularPrestamoMaximo()` cambia de firma a `(BigDecimal avaluoReal, PlazoParametro parametro)`.
- `prestamil-backend/src/test/java/com/ignis/prestamil/service/ContratoServiceTest.java` (nuevo) - 4 pruebas Mockito/AssertJ de regresión de brecha de confianza y rechazos D-04/D-05.

## Decisions Made
- `calcularPrestamoMaximo` recibe el avalúo ya calculado (no el `PartidaContratoRequest`) para evitar que una futura refactorización reintroduzca accidentalmente la lectura de `pr.getAvaluoReal()`.
- Detección de ALHAJA por `tipoPrenda.getTipo().equalsIgnoreCase("ALHAJA")` en vez de comparar `id == 1`, siguiendo la nota de robustez del plan.

## Deviations from Plan

**Repositorio dual detectado durante la ejecución (no es una desviación de código, es un detalle operativo):** El proyecto tiene un repo git anidado en `prestamil-backend/.git` (rama `manu`), separado del repo raíz que versiona `.planning/`. `config.json` reporta `sub_repos: []`, por lo que no había ruteo automático configurado. Los commits de código (Task 1 y Task 2) se hicieron directamente en el repo anidado `prestamil-backend`; este commit de metadatos (SUMMARY/STATE/ROADMAP) se hace en el repo raíz. Esto es consistente con cómo otros planes de la Phase 4 (04-01) ya estaban commiteando en paralelo en el mismo repo anidado.

None - plan de código ejecutado exactamente como fue escrito. No se requirieron auto-fixes de Reglas 1-3 ni decisiones arquitectónicas (Regla 4).

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ORO-03 satisfecho para el lado `ContratoService`: el avalúo real de ALHAJA ya es 100% server-side.
- ORO-04 cubierto parcialmente (lado `ContratoService`) con prueba de regresión explícita; queda pendiente la porción de ORO-04 que corresponda a otros componentes fuera de este plan (ej. `PlazoService.recalcularRegistros`, cubierto en 04-01/04-02).
- Este plan no tocó `oro_tabla_prestamo` ni el cálculo de `PlazoHechuraAlhaja.precioPrestamo` — solo consume el valor ya almacenado en esa columna, por lo que es independiente de 04-01/04-02 y no bloquea ni depende de ellos.
- Sin bloqueos conocidos para Phase 5 (Beneficiario Obligatorio) ni Phase 6 (Motor de Plata).

---
*Phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza*
*Completed: 2026-07-03*

## Self-Check: PASSED

- FOUND: prestamil-backend/src/main/java/com/ignis/prestamil/service/ContratoService.java
- FOUND: prestamil-backend/src/test/java/com/ignis/prestamil/service/ContratoServiceTest.java
- FOUND: .planning/phases/04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza/04-03-SUMMARY.md
- FOUND commit: 1c5059f (prestamil-backend repo)
- FOUND commit: 5622f04 (prestamil-backend repo)
