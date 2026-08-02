# Prestamil

## What This Is

Prestamil es un sistema de gestión de casa de empeño (pawnshop) compuesto por un backend Spring Boot 3.2.5 con API REST y un frontend Angular SPA. Gestiona clientes, prendas, contratos de empeño, plazos, usuarios, roles, turnos y configuración de la sucursal. La comunicación usa autenticación por sesión con cookie JSESSIONID almacenada en MariaDB vía Spring Session JDBC.

## Core Value

El sistema debe permitir registrar y consultar clientes y contratos de empeño de forma rápida y confiable — si la búsqueda de clientes o el registro de contratos falla, el negocio se detiene.

## Current Milestone: v1.1 Motor de Cálculo Real y Ciclo de Vida del Contrato

**Goal:** Replicar el cálculo de préstamo/avalúo del sistema legacy COCAE (oro, plata, varios) y completar el ciclo de vida operativo del contrato (refrendos, sanciones, reposición, folio, impresión, reportes).

**Target features:**
- Plazos/periodos por tipo de operación (Alhajas/Plata/Varios), periodos Diario/Semanal/Quincenal/Mensual configurables
- Motor de cálculo de oro replicando COCAE exacto (tabla real %Prestamo por kilataje×hechura, base 24K, recálculo automático de todas las tablas)
- Separación avalúo vs préstamo — préstamo ajustable manualmente solo hacia abajo, nunca sobre el límite calculado
- Reglas por tipo de pieza: Alhajas (kilataje+plazo), Plata (leyes 925/725), Varios/electrónicos (lógica propia)
- Tabla de amortización/vencimientos calculada al vuelo (solo guarda fecha inicial + vencimiento final)
- Sanción por extemporaneidad: 2% semanal en refrendo tardío
- Reimpresión y reposición de contrato con cobro correcto en caja/reportes (corrige discrepancia actual)
- Folio automático consecutivo; beneficiario obligatorio
- Reporte/PDF de referencia (plazo, periodo, quilataje, avalúo, préstamo) usable offline
- Ajuste de plantilla de impresión de contrato (hoja oficio, sin desacomodo)

## Requirements

### Validated

- ✓ Autenticación por sesión (login/logout, JSESSIONID en MariaDB) — existente
- ✓ Control de turnos (abrir/cerrar turno, forzar logout por SSE) — existente
- ✓ CRUD de clientes con búsqueda por nombre completo y teléfono — existente
- ✓ CRUD de usuarios con roles y permisos por menú — existente
- ✓ Catálogo de prendas (tipos, subtipos, valores de atributo) — existente
- ✓ Configuración de plazos y parámetros de préstamo por tipo de prenda — existente
- ✓ Gestión de sucursal con caché Caffeine — existente
- ✓ Generación de reportes PDF con JasperReports — existente
- ✓ Soporte de impresora térmica ESC/POS — existente
- ✓ Migraciones de BD versionadas con Liquibase — existente
- ✓ Búsqueda de clientes con FULLTEXT index (MATCH/AGAINST) — Phase 1
- ✓ Tabla de precios de hechuras de alhajas por plazo/sucursal/kilataje (`PlazoHechuraAlhaja`) — Phase 2
- ✓ Motor de precio del oro simplificado (3 factores globales por hechura + escalado lineal por kilataje) — Phase 2 (reemplazado en Phase 4 por el motor exacto de COCAE)
- ✓ Registro de contratos de empeño (`Contrato`/`PartidaContrato`/`MovimientoContrato`) con fórmula de avalúo simplificada — Phase 3
- ✓ Componente Avaluos conectado a APIs reales (selección cliente, plazo, prendas, confirmación de contrato) — Phase 3
- ✓ Motor de cálculo de oro que replica exacto los montos de COCAE — tabla real de 24 celdas (`oro_tabla_prestamo`) importada vía Liquibase 012, `PlazoService.recalcularRegistros` deriva `precioBase` por celda en vez de 3 factores globales, verificado con `compareTo()` contra capturas reales (ORO-01, ORO-02, ORO-04) — Phase 4
- ✓ Servidor recalcula el avalúo real de partidas ALHAJA (`ContratoService.calcularAvaluoRealAlhaja`), cerrando la brecha de confianza con el cliente; préstamo ajustable solo a la baja del límite calculado; kilataje 24K y kilatajes no soportados rechazados explícitamente (ORO-03, ORO-04) — Phase 4
- ✓ Pantalla de administración "Configuración del Oro" para editar el `%Prestamo` de la tabla real de 24 celdas (antes solo editable vía changeset Liquibase) — recalcula en cascada `precioBase` en todos los `PlazoHechuraAlhaja` preservando `porcAumento` por plazo, 24K referencia no editable, factores de hechura eliminados en 4.1 y **reinstaurados como factor configurable por sucursal** en el quick task 260726-lin (ORO-05, ORO-06, ORO-07, ORO-09) — Phase 4.1

### Active

- [ ] Plazos/periodos configurables por tipo de operación (Alhajas/Plata/Varios) y por periodicidad (Diario/Semanal/Quincenal/Mensual) con número de periodos variable
- [ ] Cálculo de plata con leyes 925 y 725
- [ ] Cálculo de varios/electrónicos con lógica propia (no hereda la regla de oro)
- [ ] Tabla de amortización/vencimientos calculada al vuelo a partir de fecha inicial + fecha de vencimiento final (sin persistir fechas intermedias)
- [ ] Sanción de 2% semanal por refrendo extemporáneo, incluida en el cálculo y en el contrato impreso
- [ ] Reimpresión y reposición de contrato con cobro correctamente reflejado en caja y reportes
- [ ] Folio de contrato generado automática y consecutivamente
- [ ] Beneficiario obligatorio en el contrato
- [ ] Reporte/PDF de referencia (plazo, periodo, quilataje, avalúo, préstamo) usable como respaldo operativo offline
- [ ] Plantilla de impresión de contrato ajustada a hoja oficio sin desacomodo ni cortes

### Out of Scope

- Índice en `telefono` — ya existe como UNIQUE KEY en la tabla `clientes`
- Réplica de la lógica de venta/fundición de COCAE (Ventas al Público, Apartado de Prendas) — el motor de oro de v1.1 cubre solo el lado préstamo/avalúo

## Context

- Stack: Java 21 / Spring Boot 3.2.5 / Spring Data JPA / MariaDB / Liquibase / Angular 20
- Los changesets de Liquibase están en `src/main/resources/db/changelog/changes/` con prefijo numérico
- Sistema legacy de referencia: **COCAE v3.80** — el usuario tiene acceso operativo (no de código fuente) y puede sacar capturas de pantalla de tablas de configuración y contratos reales para verificar fórmulas
- **Confirmado (2026-07-03) con capturas de COCAE de dos plazos distintos (DIARIO tabla 7, SEMANAL tabla 8):** la tabla `%Prestamo` (8 kilates × 3 hechuras = 24 celdas) es **global por sucursal, no varía por plazo** — el `precio_base` es idéntico entre plazos; solo el `% Aumento` es específico de cada plazo (Diario=7%, Semanal=10%). Cadena de fórmula completa confirmada:
  1. `PrecioAvaluo(kilate) = PrecioGramoBase21K × (kilate/21)` — global, desde pantalla "Precio del Oro"
  2. `PrecioBase(kilate,hechura) = PrecioAvaluo(kilate) × %Prestamo(kilate,hechura) / 100` — tabla global de 24 celdas, irregular, no es fórmula (ver tabla abajo)
  3. `PrecioPrestamo(kilate,hechura,plazo) = PrecioBase(kilate,hechura) × (1 + %Aumento_plazo/100)` — `%Aumento` es plano por plazo
- Tabla `%Prestamo` completa recalculada desde capturas limpias (precio base 21K = 1679.50):

  | Kilate | Fundir | Normal | Especial |
  |---|---|---|---|
  | 6K | 24.76% | 26.79% | 30.73% |
  | 8K | 60.11% | 62.04% | 64.10% |
  | 10K | 61.05% | 62.63% | 64.15% |
  | 12K | 61.63% | 62.95% | 64.24% |
  | 14K | 62.14% | 63.27% | 64.39% |
  | 18K | 62.52% | 63.40% | 66.34% |
  | 21K | 62.67% | 63.44% | 66.08% |
  | 24K | 0% | 0% | 0% |

  Esta tabla mapea directamente a `PlazoHechuraAlhaja`: `precio_base` (paso 2, global) y `porc_aumento` (paso 3, ya se almacena por plazo correctamente — el bug está solo en cómo se deriva `precio_base` en `PlazoService.recalcularRegistros`, que hoy usa 3 factores globales en vez de esta tabla de 24 celdas).
- **Factor de ajuste por hechura (2026-07-26):** además del `%Prestamo` de la tabla de 24 celdas, existe un factor configurable por hechura (Fundir/Normal/Especial) y por sucursal, almacenado en `precio_oro`, con seed neutro 100.00%. **Confirmado por el usuario: SÍ afecta el monto real del préstamo** — se aplica tanto al Precio Prestamo de la pantalla Configuración del Oro como a `PlazoHechuraAlhaja.precioBase` (motor de plazos), de modo que ambos coinciden. Los contratos ya emitidos no se recalculan. Pendiente de confirmar con capturas de COCAE únicamente los valores reales por hechura.
- `PlazoHechuraAlhaja` (clave: plazo+sucursal+kilataje+hechura) ya soporta granularidad por celda a nivel de schema — el cambio es en la lógica de recálculo (`PlazoService.recalcularRegistros`), no en el modelo de datos
- Documento de diseño existente: `.planning/codebase/AVALUOS.md` (fórmulas y decisiones de negocio previas — varias quedaron resueltas en la reunión con Jorge: libre avalúo solo a la baja, beneficiario obligatorio, sanción 2%/semana, reposición de contrato confirmada)
- Bug conocido: discrepancia entre lo cobrado por reposición/reimpresión de contrato y lo reflejado en reportes de caja — corregir como parte de este milestone
- CORS solo permite `http://localhost:4200`; el sistema corre en un solo servidor
- **Reconciliación 2026-08-02:** se encontraron commits del 2026-07-26 hechos directamente por el usuario en ambos repos, fuera del flujo GSD, sin documentar hasta ahora. Incluyen groundwork real de Phase 6/7 (ver `ROADMAP.md`/`REQUIREMENTS.md`) y un cambio de `UsuarioService.deleteById` de soft-delete a hard-delete físico que **contradice la tabla de arquitectura de `CLAUDE.md`** ("Soft-delete estatus=false") — pendiente decidir si se revierte el código o se actualiza `CLAUDE.md`. Detalle completo en `STATE.md`.

## Constraints

- **Tech stack**: MariaDB — FULLTEXT en InnoDB está disponible desde MariaDB 10.0+
- **Migración**: Todos los cambios de schema deben ir en un changeset Liquibase numerado
- **Compatibilidad**: La query MATCH/AGAINST debe usar el mismo parámetro de búsqueda que el endpoint actual (`/api/clientes/search?q=`)
- **Fidelidad de cálculo**: Los montos de préstamo/avalúo de oro deben coincidir con los que produce COCAE para los mismos insumos (kilataje, hechura, peso, precio base) — no basta con que "parezca razonable"

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| FULLTEXT index en lugar de B-tree compuesto | La query actual es `LIKE '%term%'` — B-tree no puede usar leading wildcard | ✓ Good |
| MATCH IN BOOLEAN MODE | Permite búsqueda parcial de palabras (equivalente al LIKE '%term%' actual) | ✓ Good |
| Motor de oro replica exacto los montos de COCAE (tabla real de %Prestamo por kilataje×hechura) en vez de una fórmula simplificada nueva | Evitar discrepancias frente a montos que cajeros/clientes ya conocen del sistema legacy — decisión confirmada explícitamente en vez de asumir una fórmula | ✓ Good — implementado y verificado en Phase 4 (changeset 012, `PlazoServiceTest` parity contra COCAE) |
| Préstamo ajustable solo hacia abajo del límite calculado (nunca hacia arriba) | Regla de negocio confirmada por Jorge — control de riesgo sobre el avalúo | — Pending |
| Beneficiario del contrato es obligatorio (antes se documentó como opcional en AVALUOS.md) | Aclarado en reunión con Jorge | — Pending |
| Reinstaurar el factor por hechura como configurable por sucursal y propagarlo al monto real del préstamo (revierte D-17 de Phase 4.1) | El usuario confirmó que aplica en COCAE producción y que debe afectar el monto prestado, no solo la pantalla de referencia; se elimina el hardcode 90/100/110 y se hace editable, con seed neutro para no alterar montos vigentes | Pending verification |

## Evolution

Este documento evoluciona en cada transición de fase y milestone.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-02 — reconciliado con commits directos del usuario del 2026-07-26 (fuera de GSD) en backend/frontend: groundwork de Phase 6/7, cambio de soft-delete a hard-delete en UsuarioService, nuevos endpoints de administración de plazos. Phase 4.1 (Configuración del Oro) completa desde 2026-07-26; quick task 260726-lin Tasks 1-4 completas, Task 5 de verificación humana sigue pendiente*
