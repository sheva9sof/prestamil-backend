# Phase 4: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza - Context

**Gathered:** 2026-07-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Los montos de préstamo/avalúo de piezas de oro (ALHAJA) deben coincidir exactamente con los que produce el sistema legacy COCAE para los mismos insumos, y el servidor deja de confiar en el `avaluoReal` que envía el cliente al calcular el techo de préstamo. Es un phase de corrección de lógica de cálculo backend (datos + recálculo), no de construcción de UI nueva.

</domain>

<decisions>
## Implementation Decisions

### Fuente de la tabla de 24 celdas
- **D-01:** La tabla de `%Prestamo` (8 kilates × 3 hechuras = 24 celdas) se importa vía changeset Liquibase numerado como datos fijos — global por sucursal, no varía por plazo (confirmado con capturas reales de dos plazos distintos, DIARIO tabla 7 y SEMANAL tabla 8, con `precio_base` idéntico entre ambos).
- **D-02:** No se construye ninguna pantalla nueva para editar las 24 celdas en este phase. Si el negocio cambia sus reglas de margen en el futuro, se hace vía un nuevo changeset. La pantalla "Precio del Oro" existente (`PrecioOroController`/`PrecioOroRequest`) se mantiene tal cual, solo para precio del gramo y los 3 factores de hechura de referencia (que dejan de ser el mecanismo real de cálculo del préstamo, pero se conservan porque otras partes de la UI ya los usan).
- **D-03 (Deferred → backlog):** Una pantalla de administración para editar las 24 celdas directamente (espejo de la UI de COCAE) queda anotada como idea futura, no como parte de este phase.

### Kilataje 24K y kilatajes no soportados
- **D-04:** Una partida ALHAJA con kilataje 24K debe ser rechazada explícitamente por el backend con un mensaje claro tipo "Oro de 24K no es prendable" — no dejar que la validación genérica de "importe mínimo de préstamo" la rechace con un mensaje confuso, dado que $0 no es realmente un préstamo rechazado por monto bajo sino una regla de negocio distinta.
- **D-05:** Si llega al backend un kilataje que no es ninguno de los 8 soportados por COCAE (6/8/10/12/14/18/21/24), el servidor debe rechazar con `BadRequestException` ("Kilataje no soportado: {valor}"). No se interpola entre celdas — COCAE mismo no lo hace, solo maneja esos 8 valores fijos.

### Verificación de paridad con COCAE
- **D-06:** La verificación de que los montos calculados coinciden con COCAE debe ser exacta al centavo, usando `compareTo()` (nunca `equals()`) en pruebas unitarias contra capturas reales de COCAE ya documentadas en `PROJECT.md` (precio base 21K = 1679.50, tabla completa de 24 celdas confirmada). Documentar explícitamente en qué paso de la cadena de cálculo se redondea (contrato de redondeo), no solo comparar el resultado final.

### Brecha de confianza servidor/cliente
- **D-07:** El servidor recalcula el avalúo real de una partida ALHAJA en `ContratoService.buildPartida()`/`calcularPrestamoMaximo()` a partir de `PlazoHechuraAlhaja` (kilataje, hechura, peso) — el `avaluoReal` que envía el cliente deja de tener efecto sobre el techo de préstamo. Esto cierra la Pitfall 1 documentada en `.planning/research/PITFALLS.md`.
- **D-08:** Si el precio del oro cambia entre que el cajero abre el formulario de Avalúos y confirma el contrato, y el avalúo recalculado por el servidor difiere del que se mostró en pantalla, el sistema usa el valor del servidor sin fricción adicional (no se agrega advertencia ni re-confirmación de UI). El servidor es la única fuente de verdad. No se requiere cambio de frontend para esto en este phase.

### Contratos ya existentes
- **D-09:** Los contratos ya abiertos (estatus VIGENTE) creados con la fórmula anterior (3 factores globales) **no se recalculan retroactivamente**. `PartidaContrato.avaluoContrato`/`montoPrestamo` ya están snapshoteados al momento de creación — son un acuerdo ya aceptado por el cliente. Solo los contratos nuevos, creados después de este phase, usan la fórmula corregida. No se necesita ninguna migración de datos sobre `contrato`/`partida_contrato`.

### Recalculo por celda al cambiar precio del oro
- **D-10 (ya en REQUIREMENTS.md, confirmado):** `PlazoService.recalcularRegistros` debe conservar el `porcAumento` propio de cada celda (kilataje×hechura×plazo) al recalcular por cambio del precio base del oro — solo `precio_base` se deriva de la tabla global de 24 celdas × precio del gramo vigente; `porc_aumento` no se toca.

### Claude's Discretion
- Formato exacto del changeset Liquibase (número de secuencia, nombre de tabla si se decide una tabla nueva vs. reutilizar `plazo_hechura_alhaja` con un origen de datos distinto) — decisión de implementación, no de negocio.
- Si documentar el contrato de redondeo (D-06) como comentario Javadoc, README interno, o test dedicado — cualquiera es aceptable mientras sea explícito y verificable.
- Mensaje de error exacto para D-04/D-05 (texto en español, consistente con el resto de excepciones de negocio del proyecto).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Datos confirmados de COCAE (fuente de verdad para la tabla de 24 celdas)
- `.planning/PROJECT.md` (sección Context) — cadena de fórmula completa confirmada (PrecioAvaluo → PrecioBase vía tabla de 24 celdas → PrecioPrestamo vía %Aumento por plazo) y la tabla `%Prestamo` completa (8 kilates × 3 hechuras), derivada de capturas reales de COCAE v3.80 (plazos DIARIO tabla 7 y SEMANAL tabla 8, precio base 21K = 1679.50)
- `.planning/codebase/AVALUOS.md` — diseño original del módulo Avaluos, fórmulas previas (ahora parcialmente superadas por el hallazgo de la tabla de 24 celdas) y decisiones de negocio pendientes (§7)

### Requirements y roadmap
- `.planning/REQUIREMENTS.md` — ORO-01, ORO-02, ORO-03, ORO-04 (definición formal de los requisitos de este phase)
- `.planning/ROADMAP.md` — Phase 4: goal, success criteria, dependencias (Phase 3)

### Investigación técnica (research previa a este milestone)
- `.planning/research/ARCHITECTURE.md` — puntos de integración exactos: `PlazoService.recalcularRegistros()` (fix de derivación de precio_base), `ContratoService.buildPartida()`/`calcularPrestamoMaximo()` (cierre de brecha de confianza)
- `.planning/research/PITFALLS.md` — Pitfall 1 (servidor confía en `avaluoReal` del cliente), Pitfall 2 (BigDecimal scale/rounding drift entre pasos de cálculo)
- `.planning/research/SUMMARY.md` — racional de por qué Phase 4 va primero (bloquea todo lo demás)

### Convenciones de código
- `.planning/codebase/CONVENTIONS.md` — patrón de capas Controller→Service→Repository, `BaseService`/`BaseRepository`, tipos de excepción (`BadRequestException` para D-04/D-05), Javadoc en español, DTOs hand-written (no MapStruct pese a estar declarado en pom.xml)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PlazoHechuraAlhaja` (clave: plazo+sucursal+kilataje+hechura, campos `precioBase`/`porcAumento`/`precioPrestamo`) — ya modela correctamente la granularidad de 24 celdas por plazo; no requiere cambio de schema, solo corregir cómo se deriva `precioBase`
- `PrecioOro` (entidad: `precioGramo24k`/`baseKilataje`/`calcularSobre`/`factorFundir`/`factorNormal`/`factorEspecial`) — pantalla y modelo existentes para el precio del gramo; se mantienen, dejan de ser el mecanismo de cálculo real del préstamo
- `PlazoService.recalcularRegistros()` (líneas ~304-324) — punto único de recálculo al cambiar el precio del oro; hoy multiplica por `factorHechura` (3 valores globales), debe multiplicar por el `%Prestamo` de la celda (24 valores, tabla global nueva)
- `ContratoService.buildPartida()`/`calcularPrestamoMaximo()` — punto donde hoy se confía en `pr.getAvaluoReal()` del cliente; debe recalcular server-side para ALHAJA

### Established Patterns
- Excepciones de negocio: `BadRequestException` (400) para violaciones de regla de negocio en la capa de servicio — usar para D-04/D-05
- BigDecimal: construcción por String, `RoundingMode.HALF_UP`, `.setScale()` explícito — ya es la convención del proyecto, mantenerla; comparar con `compareTo()` nunca `equals()`
- Changesets Liquibase: SQL-formatted, numerados secuencialmente (`011-oro-sancion-plata.sql` es el último) — el siguiente sería `012-*`

### Integration Points
- Cambio de lógica en `PlazoService.recalcularRegistros()` — no cambia su firma pública, solo la fórmula interna
- Nuevo método privado en `ContratoService` para recomputar avalúo real de ALHAJA (análogo a como ya se llama `calcularAvaluoContrato` para el avalúo de contrato)
- Posible tabla/changeset nuevo para almacenar la tabla global de 24 celdas (`%Prestamo` por kilataje×hechura×sucursal) como fuente de la que se deriva `precio_base` — decisión de implementación para el planner

</code_context>

<specifics>
## Specific Ideas

- Precio base 21K de referencia usado en las capturas de COCAE: 1679.50 (sucursal Tierra Colorada)
- Tabla `%Prestamo` completa confirmada (ver `.planning/PROJECT.md` Context para el detalle):

  | Kilate | Fundir | Normal | Especial |
  |---|---|---|---|
  | 6K | 24.76% | 26.79% | 30.73% |
  | 8K | 60.11% | 62.04% | 64.10% |
  | 10K | 61.05% | 62.63% | 64.15% |
  | 12K | 61.63% | 62.95% | 64.24% |
  | 14K | 62.14% | 63.27% | 64.39% |
  | 18K | 62.52% | 63.40% | 66.34% |
  | 21K | 62.67% | 63.44% | 66.08% |
  | 24K | 0% | 0% | 0% (bloqueado, D-04) |

- `% Aumento` es plano por plazo, ejemplos confirmados: Diario=7%, Semanal=10%, y una tercera tabla vista antes con 3% (plazo no identificado con certeza, pero consistente con el patrón)

</specifics>

<deferred>
## Deferred Ideas

- UI de administración para editar la tabla de 24 celdas directamente desde Prestamil (espejo de la pantalla "Precio del Oro" de COCAE) — D-03, futuro phase
- Recalculo retroactivo de contratos VIGENTE ya abiertos — descartado explícitamente (D-09), no es una idea a retomar salvo que el negocio lo pida explícitamente

### Reviewed Todos (not folded)
Ninguno — no había todos pendientes que hicieran match con este phase (`todo match-phase 4` no encontró coincidencias)

</deferred>

---

*Phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza*
*Context gathered: 2026-07-03*
