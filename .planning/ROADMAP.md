# Roadmap: Prestamil

## Overview

**Milestone v1.0 — DB Indexes / Avaluos Base (COMPLETADO):** Optimización de búsqueda de clientes (FULLTEXT) y construcción del core de contratos de empeño (`Contrato`/`PartidaContrato`/`MovimientoContrato`) con una primera fórmula de avalúo simplificada.

**Milestone v1.1 — Motor de Cálculo Real y Ciclo de Vida del Contrato (EN CURSO):** La investigación confirmó que el motor de oro, plata, beneficiario y sanción ya tienen una primera implementación en el codebase — este milestone es una pasada de corrección/cierre de brechas (fidelidad contra COCAE + cierre de brecha de confianza servidor/cliente), no un build desde cero. Los phases siguen el orden de dependencia recomendado por la investigación: oro primero (bloquea todo lo demás y es la única brecha de confianza activa), beneficiario temprano (trivial y aislado), plata en paralelo (código independiente), sanción al final (depende de que el monto de préstamo de oro ya sea correcto). REPORT-01 (corte de caja) y PDF-01 (reimpresión) quedan explícitamente diferidos a v2 por decisión del usuario.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3...): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: FULLTEXT Search** - Migración Liquibase, entity annotation, repository native queries y service merge — búsqueda de clientes optimizada end-to-end — COMPLETADA (260514-x0j, commit 1f13c25)
- [x] **Phase 2: Módulo PlazoHechuraAlhaja** - Liquibase 006, 5 endpoints nuevos, plazo.service.ts, refactor plazos-periodos — COMPLETADA (260516-mns, commits a221487/ed2cf35)
- [x] **Phase 3: Módulo Avaluos Fase A Backend** - Liquibase 007/008, entidades JPA Contrato/PartidaContrato/MovimientoContrato, repositorios, DTOs, ContratoMapper, ContratoService, ContratoController (5 endpoints REST), frontend conectado a APIs reales — COMPLETADA (260522-h4a, commit pendiente)
- [x] **Phase 4: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza** - Tabla real de 24 celdas importada por Liquibase, recálculo por celda, avalúo ALHAJA recomputado server-side, verificación compareTo() contra COCAE — COMPLETADA (verificado 2026-07-03)
- [x] **Phase 4.1: Configuración del Oro — Admin UI (INSERTED)** - Pantalla dedicada para editar el %Prestamo de las 24 celdas, recálculo en cascada, 24K no editable — COMPLETADA (verificado 2026-07-26); factor de hechura eliminado en esta fase y luego reinstaurado por el quick task 260726-lin (ORO-09, Tasks 1-4 completas, Task 5 de verificación humana pendiente)
- [ ] **Phase 5: Beneficiario Obligatorio** - Backfill + NOT NULL en `nombre_beneficiario`, validación backend `@NotBlank`, campo requerido en frontend de Avalúos
- [ ] **Phase 6: Motor de Plata** - Cálculo de avalúo por ley (925/720) server-side, cierre de brecha de confianza para PLATA, límite de préstamo ajustable solo a la baja
- [ ] **Phase 7: Sanción por Extemporaneidad — Verificación** - Redondeo de semanas vencidas verificado contra COCAE, corrección de refrendo multi-periodo, monto de sanción expuesto en MovimientoResponse

## Phase Details

### Phase 1: FULLTEXT Search — COMPLETADA ✅
**Goal**: La búsqueda de clientes usa FULLTEXT index en lugar de LIKE '%term%'; el schema está versionado y el comportamiento es equivalente al anterior
**Depends on**: Nothing (first phase)
**Requirements**: SRCH-01, SRCH-02, SRCH-03, SRCH-04
**Completed**: 2026-05-15 (changeset 004-search-indexes.sql, commit 1f13c25)

### Phase 2: Módulo PlazoHechuraAlhaja — COMPLETADA ✅
**Goal**: Tabla de precios de hechuras de alhajas por plazo/sucursal/kilataje — CRUD completo backend + integración frontend
**Completed**: 2026-05-16 (changeset 006-plazos-sucursal.sql, commits a221487/ed2cf35)
**Deliverables**: 5 endpoints (`/api/plazos/{id}/alhajas`, PUT precio-oro, etc.), `plazo.service.ts` ampliado, tab Parámetros editable en PlazosPeriodosComponent

### Phase 3: Módulo Avaluos Fase A Backend — COMPLETADA ✅
**Goal**: Entidades, repositorios, servicio y controlador de contratos de empeño — core del negocio
**Completed**: 2026-05-22 (changesets 007-contratos.sql + 008-menu-avaluos.sql, commit pendiente)
**Deliverables**: Entidades JPA (`Contrato`, `PartidaContrato`, `MovimientoContrato`), `ContratoService`, `ContratoController` (POST + 4 GETs), frontend `avaluo.component.ts` conectado a APIs reales
**Pending (Fase B/C)**: PDF contrato, refrendos, finiquitos, listado paginado

### Phase 4: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza — COMPLETADA ✅
**Goal**: Los montos de préstamo/avalúo de piezas de oro coinciden exactamente con los de COCAE y el servidor nunca confía en el avalúo que envía el cliente
**Depends on**: Phase 3 (requiere `Contrato`/`PartidaContrato`/`PlazoHechuraAlhaja` ya existentes)
**Requirements**: ORO-01, ORO-02, ORO-03, ORO-04
**Completed**: 2026-07-03 (verificado, 7/7 must-haves, ver `04-VERIFICATION.md`)
**Success Criteria** (what must be TRUE):
  1. El sistema tiene persistida la tabla real de COCAE (24 celdas: 8 kilates × 3 hechuras) vía un changeset Liquibase numerado, reemplazando el cálculo por 3 factores globales
  2. Al cambiar el precio base del oro, `PlazoService.recalcularRegistros` recalcula cada celda conservando su `porcAumento` propio, sin sobreescribirlo con un factor derivado global
  3. Al crear un contrato con una partida ALHAJA, el avalúo real que queda registrado es el que calcula el servidor a partir de `PlazoHechuraAlhaja` (kilataje, hechura, peso) — un valor `avaluoReal` distinto enviado por el cliente no tiene efecto
  4. Para un conjunto de insumos de prueba (kilataje, hechura, peso, precio base) tomados de capturas reales de COCAE, el monto calculado por el sistema coincide exactamente (`compareTo()`) con el monto legacy
**Plans**: 3 plans

Plans:
- [x] 04-01-PLAN.md — Changeset Liquibase 012 (tabla oro_tabla_prestamo, 24 celdas COCAE) + entidad/repositorio JPA de solo lectura
- [x] 04-02-PLAN.md — Reescribir PlazoService.recalcularRegistros para usar oro_tabla_prestamo en vez de 3 factores globales, preservando porcAumento por celda; PlazoServiceTest con paridad COCAE
- [x] 04-03-PLAN.md — Cerrar brecha de confianza en ContratoService (calcularAvaluoRealAlhaja server-side, rechazo 24K/kilataje no soportado); ContratoServiceTest de regresión

### Phase 04.1: Configuración del Oro — Admin UI para tabla de 24 celdas (INSERTED) — COMPLETADA ✅

**Goal**: El negocio puede editar el `%Prestamo` de las 24 celdas de `oro_tabla_prestamo` (8 kilates × 3 hechuras) desde una pantalla dedicada, en vez de solo vía changeset Liquibase — retoma la decisión D-03 (deferred) de Phase 4
**Depends on**: Phase 4 (requiere `oro_tabla_prestamo` y `PlazoService.recalcularRegistros` ya corregidos)
**Requirements**: ORO-05, ORO-06, ORO-07, ORO-08 (ORO-08 superseded por ORO-09, ver abajo)
**Completed**: 2026-07-26 (verificado, 6/6 must-haves, ver `04.1-VERIFICATION.md`). Nota posterior: el quick task `260726-lin` (2026-07-26) reinstauró el factor de ajuste por hechura que esta fase había eliminado — ver `ORO-09` en `REQUIREMENTS.md` y el bloque "D-17 REVERTIDA PARCIALMENTE" en `04.1-CONTEXT.md`. Tasks 1-4 de ese quick task están completas; Task 5 (verificación humana) sigue pendiente.
**Success Criteria** (what must be TRUE):
  1. Existe un nuevo submenú "Configuración del Oro" bajo el menú Configuración, con una pantalla de 3 pestañas (Fundir/Normal/Especial) mostrando una tabla de 8 kilates (6,8,10,12,14,18,21,24K) con columnas Precio Avalúo (solo lectura), % Prestamo (editable) y Precio Prestamo de referencia (solo lectura)
  2. Guardar el % Prestamo de una celda persiste el cambio en `oro_tabla_prestamo` y recalcula en cascada el `precioBase` de esa celda en todos los `PlazoHechuraAlhaja` existentes, preservando el `porcAumento` propio de cada plazo (mismo mecanismo que `PlazoService.recalcularRegistros` ya usa para cambios de precio del gramo)
  3. La fila 24K se muestra deshabilitada (no editable) tanto en frontend como rechazada en backend si se intenta editar, consistente con la regla D-04 de Phase 4 (oro 24K no es prendable)
  4. La tarjeta "Precio del Oro (global)" desaparece de `/plazos-periodos`; su campo de precio del gramo se muda a la pantalla nueva, y los 3 campos "factor de hechura" (código muerto desde Phase 4) se eliminan de la UI, los DTOs (`PrecioOroRequest`/`Response`) y el esquema de BD vía changeset Liquibase
**Plans**: 3 plans

Plans:
- [x] 04.1-01-PLAN.md — Backend: API OroTablaPrestamo (GET 24 celdas + PUT %Prestamo con rechazo 24K), cascada de recálculo en PlazoService, y limpieza de factores de hechura de precio_oro (changeset 013)
- [x] 04.1-02-PLAN.md — Frontend: pantalla Configuración del Oro (3 pestañas, 8 kilates, 24K no editable, precio del gramo), ruta, mapeo de menú y changeset 014 de alta en opciones
- [x] 04.1-03-PLAN.md — Frontend: eliminar tarjeta Precio del Oro global de /plazos-periodos y quitar factores de hechura de plazo.model.ts y del componente

### Phase 5: Beneficiario Obligatorio
**Goal**: Todo contrato de empeño tiene beneficiario capturado — ni el backend ni el frontend permiten omitirlo, y los datos históricos quedan resueltos sin romper la migración
**Depends on**: Phase 3
**Requirements**: BENEF-01, BENEF-02, BENEF-03
**Success Criteria** (what must be TRUE):
  1. Una petición de creación de contrato sin beneficiario es rechazada por el backend (validación, no error 500)
  2. Los contratos existentes con `nombre_beneficiario` nulo quedan con un valor de backfill antes de que se aplique la restricción `NOT NULL` en el changeset Liquibase
  3. En el frontend de Avalúos, el campo beneficiario es requerido y el usuario no puede confirmar el contrato sin capturarlo
**Plans**: TBD
**UI hint**: yes

### Phase 6: Motor de Plata
**Goal**: El avalúo y el préstamo máximo de piezas de plata se calculan en el servidor a partir de la ley y el precio del gramo, con el mismo control de riesgo que las piezas de oro
**Depends on**: Phase 3 (independiente de Phase 4 — código y campos distintos: `ley925`/`ley725`/`precioGramoPlata` vs `PlazoHechuraAlhaja`)
**Requirements**: PLATA-01, PLATA-02, PLATA-03
**Success Criteria** (what must be TRUE):
  1. El sistema calcula el avalúo de una pieza de plata a partir de su ley (925/720) y el precio vigente del gramo de plata, en un método server-side análogo a `calcularAvaluoContrato` de oro
  2. Al crear un contrato con una partida PLATA, el avalúo real que queda registrado es el que calcula el servidor en `ContratoService.buildPartida` — un valor enviado por el cliente no tiene efecto
  3. El préstamo máximo de una partida PLATA nunca excede el límite calculado por el servidor; el ajuste manual solo puede ser hacia la baja
**Plans**: TBD

### Phase 7: Sanción por Extemporaneidad — Verificación
**Goal**: La sanción de 2% semanal por refrendo extemporáneo se comporta de forma verificablemente idéntica a COCAE y su monto queda disponible para el contrato impreso
**Depends on**: Phase 4 (la sanción es un porcentaje del monto de préstamo, que debe ser correcto primero)
**Requirements**: SANC-01, SANC-02, SANC-03
**Success Criteria** (what must be TRUE):
  1. Para un conjunto de refrendos extemporáneos reales de COCAE, `calcularSemanasVencidas` produce el mismo número de semanas vencidas que el sistema legacy
  2. Al refrendar un contrato vencido por múltiples periodos consecutivos (plazo Diario u otro plazo corto), la nueva fecha de vencimiento se extiende correctamente sin saltarse ni duplicar periodos vencidos intermedios
  3. El monto de sanción calculado en un refrendo está disponible en `MovimientoResponse`, listo para mostrarse en el contrato impreso
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1, 2, 3, 4, 5, 6, 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. FULLTEXT Search | 1/1 | Done | 2026-05-15 |
| 2. PlazoHechuraAlhaja | 1/1 | Done | 2026-05-16 |
| 3. Avaluos Fase A Backend | 1/1 | Done | 2026-05-22 |
| 4. Motor de Oro | 3/3 | Done | 2026-07-03 |
| 4.1. Configuración del Oro — Admin UI (INSERTED) | 3/3 | Done | 2026-07-26 |
| 5. Beneficiario Obligatorio | 0/? | Not started | - |
| 6. Motor de Plata | 0/? | Not started | - |
| 7. Sanción — Verificación | 0/? | Not started | - |

**Fuera del roadmap:** quick task `260726-lin` (reinstaurar factor de ajuste por hechura, ORO-09) — Tasks 1-4 completas y commiteadas, Task 5 (verificación humana, checkpoint bloqueante) pendiente. Ver `.planning/STATE.md` y `.planning/quick/260726-lin-reinstaurar-factor-de-ajuste-por-hechura/260726-lin-SUMMARY.md`.
</content>
