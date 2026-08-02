# Requirements — Prestamil

**Project:** Prestamil — Sistema de Casa de Empeño
**Core Value:** Registrar y consultar clientes y contratos de empeño de forma rápida y confiable

---

## Milestone v1.0 — DB Indexes / Avaluos Base (COMPLETADO)

Requisitos del milestone anterior. Se conservan como registro histórico; ya no son parte del alcance activo.

### Búsqueda de Clientes

- [x] **SRCH-01:** Liquibase changeset `004-fulltext-clientes.sql` crea el índice FULLTEXT `ft_clientes_nombre` en `clientes(nombre, apellido_paterno, apellido_materno)` con directiva `--rollback` explícita, registrado en `db.changelog-master.xml`
- [x] **SRCH-02:** `ClienteRepository` reemplaza el JPQL LIKE existente con dos métodos `@Query(nativeQuery=true)`: `searchByNombreFulltext` (MATCH/AGAINST IN BOOLEAN MODE) y `searchByTelefonoPrefix` (LIKE prefix sobre UNIQUE KEY existente)
- [x] **SRCH-03:** `ClienteService.searchByNombreCompletoOrTelefono` llama ambos métodos, hace merge y deduplica por `id`; concatena `*` al término antes de llamar al método FULLTEXT
- [x] **SRCH-04:** `Cliente.java` incluye `@Index(name="ft_clientes_nombre", columnList="nombre, apellido_paterno, apellido_materno")` en `@Table(indexes=...)` como documentación del índice (sin efecto en runtime — ddl-auto=none)
- [x] **CONT-01:** La tabla `contratos` incluye índices B-tree en `numero`/`folio` y en `estatus` — satisfecho por changeset 007-contratos.sql

---

## Milestone v1.1 — Motor de Cálculo Real y Ciclo de Vida del Contrato

**Defined:** 2026-07-02
**Contexto:** Notas de reunión con Jorge + verificación directa contra capturas del sistema legacy COCAE + investigación de codebase (ver `.planning/research/SUMMARY.md`). La investigación confirmó que 6 de 8 funcionalidades pedidas ya tienen una primera implementación — este milestone es una pasada de corrección/cierre de brechas, no un build desde cero.

### v1 Requirements

#### Motor de Oro (fidelidad COCAE + cierre de brecha de confianza)

- [ ] **ORO-01:** El sistema importa la tabla real de COCAE (24 celdas: 8 kilates × 3 hechuras) como datos vía changeset Liquibase numerado, reemplazando el cálculo actual basado en 3 factores globales (Fundir/Normal/Especial)
- [ ] **ORO-02:** `PlazoService.recalcularRegistros` conserva el `porcAumento` propio de cada celda (kilataje×hechura) al recalcular por cambio del precio base del oro, sin sobreescribirlo con un factor global derivado
- [ ] **ORO-03:** El servidor recalcula el avalúo real de una partida ALHAJA a partir de `PlazoHechuraAlhaja` (kilataje, hechura, peso) en `ContratoService.buildPartida`, en vez de confiar en el `avaluoReal` que envía el cliente
- [ ] **ORO-04:** Los montos de préstamo/avalúo calculados coinciden con los que produce COCAE para los mismos insumos (kilataje, hechura, peso, precio base), verificado con capturas reales del sistema legacy y pruebas unitarias con `compareTo()`

#### Configuración del Oro — Admin UI (retoma D-03 de Phase 4)

- [x] **ORO-05:** El negocio puede editar el `%Prestamo` de las 24 celdas (8 kilates × 3 hechuras) de `oro_tabla_prestamo` desde una pantalla nueva "Configuración del Oro", en vez de únicamente vía changeset Liquibase
- [x] **ORO-06:** Al editar el `%Prestamo` de una celda, el sistema recalcula en cascada el `precioBase` de esa celda en todos los `PlazoHechuraAlhaja` existentes (todos los plazos), preservando el `porcAumento` propio de cada plazo
- [x] **ORO-07:** El kilataje 24K se muestra en la pantalla como fila de referencia no editable — el oro de 24K no es prendable (regla D-04 de Phase 4), su `%Prestamo` permanece en 0
- [x] **ORO-08:** ~~Los campos "factor de hechura" (`factorFundir`/`factorNormal`/`factorEspecial` de `PrecioOro`), sin efecto en el cálculo real desde Phase 4, se eliminan de la UI, los DTOs y el esquema de base de datos~~ **Superseded (por ORO-09, 2026-07-26):** el usuario confirmó que el factor de ajuste por hechura SÍ aplica en la operación real de COCAE — ORO-09 reinstaura el mecanismo, ahora configurable por sucursal (seed neutro 100%) y propagado también al monto real del préstamo.

#### Factor de ajuste por hechura — reinstaurado (quick task 260726-lin, 2026-07-26)

- [x] **ORO-09:** El negocio puede configurar, por sucursal, un factor de ajuste por hechura (Fundir/Normal/Especial) que se aplica como multiplicador adicional sobre el precio de préstamo, tanto en la pantalla Configuración del Oro como en el monto ofrecido en contratos nuevos (`PlazoHechuraAlhaja.precioBase`); el valor inicial es 100% (neutro) y no altera retroactivamente los contratos ya emitidos.

#### Motor de Plata

**Groundwork parcial fuera de roadmap (2026-07-03/2026-07-26, sin test, ver STATE.md):** `PartidaContrato.ley` ya existe como columna/DTO en backend (commit `7e5e4ec`) y en los modelos de frontend (commit `a796a8d`), pero **no hay ningún método de cálculo de avalúo de plata** (`calcularAvaluoPlata` o análogo) en `ContratoService` — es solo el dato, no la fórmula. Los 3 requisitos siguen sin implementar en sustancia.

- [ ] **PLATA-01:** El sistema calcula el avalúo de piezas de plata a partir de la ley (925/720, pendiente verificar cifra exacta contra COCAE) y el precio del gramo de plata, en un método server-side análogo a `calcularAvaluoContrato` de oro
- [ ] **PLATA-02:** El servidor recalcula el avalúo real de una partida PLATA en `ContratoService.buildPartida`, en vez de confiar en el valor enviado por el cliente (mismo cierre de brecha de confianza que oro)
- [ ] **PLATA-03:** El préstamo máximo para piezas de plata respeta el límite calculado por el servidor (ajuste manual solo hacia abajo, nunca por encima), igual que alhajas de oro

#### Beneficiario Obligatorio

- [ ] **BENEF-01:** El campo beneficiario es obligatorio al crear un contrato (validación `@NotNull`/`@NotBlank` en `ContratoRequest`)
- [ ] **BENEF-02:** Un changeset Liquibase hace backfill de los contratos existentes con beneficiario nulo antes de aplicar la restricción `NOT NULL` en `contrato.nombre_beneficiario`
- [ ] **BENEF-03:** El frontend de Avalúos marca el campo beneficiario como requerido y no permite confirmar el contrato sin él

#### Sanción por Extemporaneidad (verificación)

**Groundwork sustancial ya implementado fuera de roadmap (commit `7e5e4ec`, 2026-07-03), sin tests (ver STATE.md):** `MovimientoContratoController`/`MovimientoContratoService` existen completos (`refrendar`, `cobrarReposicion`, `getMovimientos`, 3 endpoints REST bajo `/api/movimientos`), con `calcularSemanasVencidas` y cálculo de `sancion` ya implementados y expuestos en `MovimientoResponse`. El frontend empezó a consumirlos el 2026-07-26 (`MovimientoService` nuevo). Falta: `MovimientoContratoServiceTest` (no existe ningún test) y la verificación de la regla de redondeo contra capturas reales de COCAE — por eso los 3 siguen marcados Pending pese al código ya funcionar.

- [ ] **SANC-01:** La regla de redondeo de semanas vencidas (`calcularSemanasVencidas`) coincide con la que usa COCAE, verificado contra capturas de refrendos extemporáneos reales
- [ ] **SANC-02:** El refrendo de un contrato vencido por múltiples periodos consecutivos (plazos cortos como Diario) extiende correctamente la fecha de vencimiento sin perder periodos vencidos intermedios
- [ ] **SANC-03:** El monto de sanción calculado queda disponible en `MovimientoResponse` para su futura visualización en el contrato impreso — **implementado** (`MovimientoContratoService` calcula y setea `sancion`), falta solo verificación end-to-end y test

### v2 Requirements (Deferred)

- **REPORT-01:** Corte de caja / reporte (`GET /api/reportes/corte-caja/{turnoId}`) — pedido explícitamente por Jorge, diferido a un milestone futuro a petición del usuario
- **PDF-01:** PDF / reimpresión de contrato (`GET /api/contratos/{id}/pdf`, plantilla ajustada a hoja oficio) — pedido explícitamente por Jorge, diferido junto con REPORT-01 porque depende de que oro/plata/sanción estén verificados primero
- **CAT-01:** Cálculo y disclosure de CAT (Costo Anual Total) — requiere revisión legal dedicada de NOM-179-SCFI-2016, no solicitado explícitamente por el cliente
- **FOLIO-02:** Numeración de folio por sucursal (multi-sucursal) — solo aplica cuando haya más de una sucursal activa; hoy `sucursalId` está hardcodeado a 1

### Out of Scope

| Feature | Reason |
|---------|--------|
| Corte de caja / reporte | Diferido explícitamente por el usuario a un milestone futuro, aunque Jorge lo pidió — se prioriza primero el motor de cálculo |
| PDF / Reimpresión de contrato | Mismo motivo — depende de que oro/plata/sanción estén verificados primero para no imprimir montos incorrectos |
| CAT (Costo Anual Total) | Requiere revisión legal dedicada de NOM-179-SCFI-2016; no solicitado explícitamente por el cliente en esta reunión |
| Tope estatutario de tasa/sanción | No existe ley vigente en México que lo exija (LFPC Art. 65 Bis, verificado en investigación) — las tasas se mantienen configurables |
| Folio por sucursal | Sistema opera con una sola sucursal (`sucursalId` hardcodeado a 1) — prematuro hasta que haya multi-sucursal real |
| Réplica de venta/fundición de COCAE | El motor de oro de v1.1 cubre solo el lado préstamo/avalúo, no las pantallas "Ventas al Público"/"Apartado de Prendas" |
| Reglas de Varios/electrónicos | Ya implementadas correctamente (avalúo manual del valuador) — sin cambios necesarios este milestone |

### Traceability

| REQ-ID | Phase | Status |
|--------|-------|--------|
| ORO-01 | Phase 4 | Complete |
| ORO-02 | Phase 4 | Complete |
| ORO-03 | Phase 4 | Complete |
| ORO-04 | Phase 4 | Complete |
| ORO-05 | Phase 4.1 | Complete |
| ORO-06 | Phase 4.1 | Complete |
| ORO-07 | Phase 4.1 | Complete |
| ORO-08 | Phase 4.1 | Superseded (por ORO-09, 2026-07-26) |
| ORO-09 | quick 260726-lin | Complete |
| PLATA-01 | Phase 6 | Pending (solo dato `ley`, sin fórmula) |
| PLATA-02 | Phase 6 | Pending |
| PLATA-03 | Phase 6 | Pending |
| BENEF-01 | Phase 5 | Pending |
| BENEF-02 | Phase 5 | Pending |
| BENEF-03 | Phase 5 | Pending |
| SANC-01 | Phase 7 | Pending (código existe desde 2026-07-03, sin test ni verificación COCAE) |
| SANC-02 | Phase 7 | Pending (código existe desde 2026-07-03, sin test ni verificación COCAE) |
| SANC-03 | Phase 7 | Pending (implementado funcionalmente, falta test/verificación) |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13/13 ✓
- Unmapped: 0

---
*Requirements defined: 2026-05-14 (v1.0), 2026-07-02 (v1.1)*
*Last updated: 2026-08-02 — ORO-05/06/07 completadas (Phase 4.1), ORO-08 superseded por ORO-09 (quick task 260726-lin, Task 5 de verificación humana pendiente); reconciliado con commits directos del usuario del 2026-07-26 fuera de GSD — groundwork de Phase 6 (campo `ley`) y Phase 7 (`MovimientoContratoController`/`Service` completo, sin tests) documentado por primera vez*
</content>
