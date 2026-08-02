# Phase 4: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza - Research

**Researched:** 2026-07-03
**Domain:** Backend calculation-engine correction (Spring Boot / JPA / BigDecimal / Liquibase) — no new libraries, no UI work
**Confidence:** HIGH (grounded in direct reads of current source: `PlazoService.java`, `ContratoService.java`, `PlazoHechuraAlhaja*.java`, `PrecioOro.java`, changeset `011-oro-sancion-plata.sql`, `avaluo.component.ts`)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** La tabla de `%Prestamo` (8 kilates × 3 hechuras = 24 celdas) se importa vía changeset Liquibase numerado como datos fijos — global por sucursal, no varía por plazo (confirmado con capturas reales de dos plazos distintos, DIARIO tabla 7 y SEMANAL tabla 8, con `precio_base` idéntico entre ambos).
- **D-02:** No se construye ninguna pantalla nueva para editar las 24 celdas en este phase. Si el negocio cambia sus reglas de margen en el futuro, se hace vía un nuevo changeset. La pantalla "Precio del Oro" existente (`PrecioOroController`/`PrecioOroRequest`) se mantiene tal cual, solo para precio del gramo y los 3 factores de hechura de referencia (que dejan de ser el mecanismo real de cálculo del préstamo, pero se conservan porque otras partes de la UI ya los usan).
- **D-03 (Deferred → backlog):** Una pantalla de administración para editar las 24 celdas directamente (espejo de la UI de COCAE) queda anotada como idea futura, no como parte de este phase.
- **D-04:** Una partida ALHAJA con kilataje 24K debe ser rechazada explícitamente por el backend con un mensaje claro tipo "Oro de 24K no es prendable" — no dejar que la validación genérica de "importe mínimo de préstamo" la rechace con un mensaje confuso.
- **D-05:** Si llega al backend un kilataje que no es ninguno de los 8 soportados por COCAE (6/8/10/12/14/18/21/24), el servidor debe rechazar con `BadRequestException` ("Kilataje no soportado: {valor}"). No se interpola entre celdas.
- **D-06:** La verificación de que los montos calculados coinciden con COCAE debe ser exacta al centavo, usando `compareTo()` (nunca `equals()`) en pruebas unitarias contra capturas reales de COCAE ya documentadas en `PROJECT.md` (precio base 21K = 1679.50, tabla completa de 24 celdas confirmada). Documentar explícitamente en qué paso de la cadena de cálculo se redondea (contrato de redondeo), no solo comparar el resultado final.
- **D-07:** El servidor recalcula el avalúo real de una partida ALHAJA en `ContratoService.buildPartida()`/`calcularPrestamoMaximo()` a partir de `PlazoHechuraAlhaja` (kilataje, hechura, peso) — el `avaluoReal` que envía el cliente deja de tener efecto sobre el techo de préstamo.
- **D-08:** Si el precio del oro cambia entre que el cajero abre el formulario de Avalúos y confirma el contrato, y el avalúo recalculado por el servidor difiere del que se mostró en pantalla, el sistema usa el valor del servidor sin fricción adicional (no se agrega advertencia ni re-confirmación de UI). No se requiere cambio de frontend para esto en este phase.
- **D-09:** Los contratos ya abiertos (estatus VIGENTE) creados con la fórmula anterior (3 factores globales) **no se recalculan retroactivamente**. Solo los contratos nuevos, creados después de este phase, usan la fórmula corregida. No se necesita ninguna migración de datos sobre `contrato`/`partida_contrato`.
- **D-10 (ya en REQUIREMENTS.md, confirmado):** `PlazoService.recalcularRegistros` debe conservar el `porcAumento` propio de cada celda (kilataje×hechura×plazo) al recalcular por cambio del precio base del oro — solo `precio_base` se deriva de la tabla global de 24 celdas × precio del gramo vigente; `porc_aumento` no se toca.

### Claude's Discretion

- Formato exacto del changeset Liquibase (número de secuencia, nombre de tabla si se decide una tabla nueva vs. reutilizar `plazo_hechura_alhaja` con un origen de datos distinto) — decisión de implementación, no de negocio.
- Si documentar el contrato de redondeo (D-06) como comentario Javadoc, README interno, o test dedicado — cualquiera es aceptable mientras sea explícito y verificable.
- Mensaje de error exacto para D-04/D-05 (texto en español, consistente con el resto de excepciones de negocio del proyecto).

### Deferred Ideas (OUT OF SCOPE)

- UI de administración para editar la tabla de 24 celdas directamente desde Prestamil (espejo de la pantalla "Precio del Oro" de COCAE) — D-03, futuro phase.
- Recálculo retroactivo de contratos VIGENTE ya abiertos — descartado explícitamente (D-09).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ORO-01 | El sistema importa la tabla real de COCAE (24 celdas: 8 kilates × 3 hechuras) como datos vía changeset Liquibase numerado, reemplazando el cálculo actual basado en 3 factores globales | §Architecture Patterns "New Table: oro_tabla_prestamo" + §Code Examples changeset `012-1`/`012-2` |
| ORO-02 | `PlazoService.recalcularRegistros` conserva el `porcAumento` propio de cada celda al recalcular por cambio del precio base del oro, sin sobreescribirlo con un factor global derivado | §Architecture Patterns "Fixing recalcularRegistros" — exact before/after diff of the method, both call sites (`actualizarTodosPrecios`, `recalcularTodasLasTablas`) |
| ORO-03 | El servidor recalcula el avalúo real de una partida ALHAJA a partir de `PlazoHechuraAlhaja` (kilataje, hechura, peso) en `ContratoService.buildPartida`, en vez de confiar en el `avaluoReal` que envía el cliente | §Architecture Patterns "Closing the trust boundary in ContratoService" — new private method `calcularAvaluoRealAlhaja`, wiring into `buildPartida`/`calcularPrestamoMaximo` |
| ORO-04 | Los montos de préstamo/avalúo calculados coinciden con los que produce COCAE para los mismos insumos, verificado con capturas reales del sistema legacy y pruebas unitarias con `compareTo()` | §Common Pitfalls "Pitfall 2 (rounding contract)" + §Code Examples "Unit test skeleton" |
</phase_requirements>

## Summary

This phase is a **pure backend correction**, not new construction: the schema shape needed (`PlazoHechuraAlhaja`, keyed by plazo+sucursal+kilataje+hechura) already exists and is correct; only the *derivation formula* inside `PlazoService.recalcularRegistros()` is wrong (it multiplies by 3 global hechura factors instead of the real, irregular 24-cell COCAE table), and `ContratoService` trusts a client-supplied `avaluoReal` instead of recomputing it server-side for ALHAJA partidas. Both fixes are small, targeted, and isolated to two existing service classes — no new controller, no new frontend work, no new architectural pattern.

The one genuinely new piece of infrastructure is where the global 24-cell `%Prestamo` table lives. It cannot be stored on `plazo_hechura_alhaja` (that table is keyed *per plazo*, but the `%Prestamo` values are confirmed global per sucursal — same across DIARIO and SEMANAL). The cleanest fit is one small new table (`sucursal_id, kilataje, hechura` → `porc_prestamo`), populated by a single Liquibase changeset with the 24 confirmed values, read once per recalculation and never written to from the running application in this phase (matches D-02/D-03 — no admin UI to edit it yet).

**Primary recommendation:** Add a new Liquibase changeset `012-oro-tabla-prestamo-cocae.sql` (table + 24-row import), rewrite `recalcularRegistros()` to look up `%Prestamo` from that new table per (sucursalId, kilataje, hechura) instead of the 3 global factors, and add a new private `ContratoService.calcularAvaluoRealAlhaja()` that replaces `pr.getAvaluoReal()` for ALHAJA partidas by reading `PlazoHechuraAlhaja.precioPrestamo × pesoGramos`, with explicit kilataje-24K and unsupported-kilataje rejections before that lookup runs.

## Architecture Patterns

### Current formula chain (confirmed, from PROJECT.md) vs. current buggy code

```
Confirmed COCAE chain (PROJECT.md, verified against 2 real plazo captures):
  1. PrecioAvaluo(kilate)          = PrecioGramoBase21K × (kilate / 21)
  2. PrecioBase(kilate,hechura)    = PrecioAvaluo(kilate) × %Prestamo(kilate,hechura) / 100   ← 24-cell table, irregular
  3. PrecioPrestamo(kilate,hechura,plazo) = PrecioBase(kilate,hechura) × (1 + %Aumento_plazo / 100)

Current buggy code (PlazoService.recalcularRegistros, private, called by
actualizarTodosPrecios() and recalcularTodasLasTablas()):
  precioBase = (precioGramoBase / baseKilataje) × kilataje × factorHechura   ← factorHechura is
                                                                                 ONE of only 3 GLOBAL
                                                                                 values (90/100/110%),
                                                                                 not the 24-cell table
  precioPrestamo = precioBase × (1 + row.porcAumento / 100)                  ← this part is already
                                                                                 correct (per-cell,
                                                                                 not overwritten)
```

The only wrong step is #2 (`precioBase` derivation). Step #3 (`precioPrestamo`) is already implemented correctly per-cell — do not touch that part of the method.

### New Table: `oro_tabla_prestamo` (global %Prestamo per sucursal)

Cannot reuse `plazo_hechura_alhaja` because that table is keyed by `(id_plazo, sucursal_id, kilataje, hechura)` — one row per **plazo**. The `%Prestamo` table is confirmed global across plazos (D-01). A new small table avoids duplicating the same 24 values once per plazo and avoids the "which plazo's copy is the source of truth" ambiguity.

```sql
--liquibase formatted sql

--changeset emm-a:012-1
--comment: Tabla global de %Prestamo COCAE (8 kilates x 3 hechuras) por sucursal.
--          Fuente de verdad para derivar precio_base en plazo_hechura_alhaja;
--          reemplaza el uso de factor_fundir/factor_normal/factor_especial de precio_oro
--          para ese propósito (esos factores se conservan como valores de referencia
--          en la pantalla "Precio del Oro", ver D-02, pero dejan de aplicarse en el cálculo).
CREATE TABLE oro_tabla_prestamo (
  sucursal_id     INT NOT NULL,
  kilataje        INT NOT NULL,
  hechura         VARCHAR(1) NOT NULL,
  porc_prestamo   DECIMAL(7,4) NOT NULL,
  actualizado_en  DATETIME NOT NULL,
  PRIMARY KEY (sucursal_id, kilataje, hechura),
  CONSTRAINT fk_oro_tabla_prestamo_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id)
);

--changeset emm-a:012-2
--comment: Import de valores reales COCAE v3.80 (capturas DIARIO tabla 7 / SEMANAL tabla 8,
--          precio base 21K = 1679.50) — sucursal 1 Tierra Colorada. 24K = 0% (D-04, no prendable).
INSERT INTO oro_tabla_prestamo (sucursal_id, kilataje, hechura, porc_prestamo, actualizado_en) VALUES
  (1, 6,  'F', 24.7600, NOW()), (1, 6,  'N', 26.7900, NOW()), (1, 6,  'E', 30.7300, NOW()),
  (1, 8,  'F', 60.1100, NOW()), (1, 8,  'N', 62.0400, NOW()), (1, 8,  'E', 64.1000, NOW()),
  (1, 10, 'F', 61.0500, NOW()), (1, 10, 'N', 62.6300, NOW()), (1, 10, 'E', 64.1500, NOW()),
  (1, 12, 'F', 61.6300, NOW()), (1, 12, 'N', 62.9500, NOW()), (1, 12, 'E', 64.2400, NOW()),
  (1, 14, 'F', 62.1400, NOW()), (1, 14, 'N', 63.2700, NOW()), (1, 14, 'E', 64.3900, NOW()),
  (1, 18, 'F', 62.5200, NOW()), (1, 18, 'N', 63.4000, NOW()), (1, 18, 'E', 66.3400, NOW()),
  (1, 21, 'F', 62.6700, NOW()), (1, 21, 'N', 63.4400, NOW()), (1, 21, 'E', 66.0800, NOW()),
  (1, 24, 'F', 0.0000,  NOW()), (1, 24, 'N', 0.0000,  NOW()), (1, 24, 'E', 0.0000,  NOW());
```

Register both changesets in `db.changelog-master.xml` as `<include file="db/changelog/changes/012-oro-tabla-prestamo-cocae.sql"/>` (single file, two `--changeset` blocks, consistent with the existing `011-oro-sancion-plata.sql` pattern of multiple changesets per file).

Entity/repository shape (new, small — mirror `PlazoHechuraAlhaja`/`Id` exactly):
- `OroTablaPrestamoId` (`@Embeddable`: `sucursalId Integer`, `kilataje Integer`, `hechura String`)
- `OroTablaPrestamo` (`@EmbeddedId id`, `porcPrestamo BigDecimal`, `actualizadoEn LocalDateTime`)
- `OroTablaPrestamoRepository extends BaseRepository<OroTablaPrestamo, OroTablaPrestamoId>` with `List<OroTablaPrestamo> findByIdSucursalId(Integer sucursalId)`

No mapper/DTO/controller needed — this table is read-only from the application's perspective in this phase (D-02/D-03: no admin screen, no API endpoint to edit it).

### Fixing `recalcularRegistros` (ORO-01, ORO-02)

Both call sites already have `sucursalId` in scope, so the new lookup fits without changing the public method signatures of `actualizarTodosPrecios(idPlazo, sucursalId, precioBaseOro)` or `recalcularTodasLasTablas(sucursalId, request, usuario)`.

```java
// PlazoService — replace the 3-factor version with a lookup against oro_tabla_prestamo.
// factorFundir/factorNormal/factorEspecial parameters are REMOVED from this method's
// signature; PrecioOro.factorFundir/Normal/Especial remain on the entity (D-02) but are
// no longer read here — they stay purely as reference/seed values shown on the "Precio
// del Oro" screen.
private void recalcularRegistros(List<PlazoHechuraAlhaja> registros,
                                  BigDecimal precioGramoBase, int baseKilataje,
                                  Integer sucursalId) {
    BigDecimal base = new BigDecimal(baseKilataje);
    BigDecimal precioPorKilatePuro = precioGramoBase.divide(base, 10, RoundingMode.HALF_UP);

    Map<String, BigDecimal> porcPrestamoPorCelda = oroTablaPrestamoRepository
            .findByIdSucursalId(sucursalId).stream()
            .collect(Collectors.toMap(
                    r -> r.getId().getKilataje() + "-" + r.getId().getHechura(),
                    OroTablaPrestamo::getPorcPrestamo));

    for (PlazoHechuraAlhaja r : registros) {
        String celda = r.getId().getKilataje() + "-" + r.getId().getHechura();
        BigDecimal porcPrestamo = porcPrestamoPorCelda.get(celda);
        if (porcPrestamo == null) {
            throw new ResourceNotFoundException(
                "No hay %Prestamo COCAE configurado para sucursal=" + sucursalId
                + ", kilataje=" + r.getId().getKilataje() + ", hechura=" + r.getId().getHechura());
        }
        // Paso 1: PrecioAvaluo(kilate) = precioPorKilatePuro * kilataje
        BigDecimal precioAvaluo = precioPorKilatePuro.multiply(new BigDecimal(r.getId().getKilataje()));
        // Paso 2: PrecioBase(kilate,hechura) = PrecioAvaluo * %Prestamo / 100  (24-cell table, irregular)
        BigDecimal precioBase = precioAvaluo
                .multiply(porcPrestamo.divide(CIEN, 10, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);
        r.setPrecioBase(precioBase);
        // Paso 3: PrecioPrestamo = PrecioBase * (1 + porcAumento_propio_de_la_celda / 100)
        //         — porcAumento NO se toca ni se deriva; es el valor ya almacenado en esta fila (D-10).
        r.setPrecioPrestamo(precioBase
                .multiply(BigDecimal.ONE.add(r.getPorcAumento().divide(CIEN, 10, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP));
    }
}
```

Update both call sites to drop the `factorFundir/factorNormal/factorEspecial` arguments and pass `sucursalId` instead:
- `actualizarTodosPrecios(idPlazo, sucursalId, precioBaseOro)`: delete the `PrecioOro` lookup block that currently only exists to fetch the 3 factors (lines ~286-290 in current code) — `sucursalId` is already a parameter, `baseKilataje` still needs to come from `PrecioOro.getBaseKilataje()` (keep that part), just drop the factor reads.
- `recalcularTodasLasTablas(sucursalId, request, usuario)`: same — keep resolving `factorFundir/Normal/Especial` for **persisting to `PrecioOro`** (D-02 says the screen/fields stay), but stop passing them into `recalcularRegistros`.

**Do not delete `PrecioOro.factorFundir/factorNormal/factorEspecial` fields or the request/response DTOs** — D-02 explicitly keeps that screen functional for other UI consumers; only its effect on the actual gold-table math is removed.

### Closing the trust boundary in `ContratoService` (ORO-03)

`buildPartida()` currently does `partida.setAvaluoReal(pr.getAvaluoReal())` and `calcularPrestamoMaximo()` reads `pr.getAvaluoReal()` directly — both are client-trusted (Pitfall 1 from milestone research). For ALHAJA (`tipoPrenda.getTipo().equalsIgnoreCase("ALHAJA")`, id 1 per `AVALUOS.md` — prefer comparing `tipo` string over the hardcoded id for robustness, but either is consistent with existing project conventions), replace with a server-computed value:

```java
private static final List<Integer> KILATAJES_COCAE = List.of(6, 8, 10, 12, 14, 18, 21, 24);

/**
 * Recalcula el avalúo real de una partida ALHAJA a partir de la tabla de precios
 * del plazo (PlazoHechuraAlhaja), ignorando el avaluoReal que envía el cliente.
 * Cierra la brecha de confianza servidor/cliente (D-07).
 *
 * @param pr         datos de la partida solicitada
 * @param plazoId    identificador del plazo (se convierte a Integer — PlazoHechuraAlhajaId
 *                   usa Integer, Plazo.id es Long)
 * @param sucursalId identificador de la sucursal
 * @return avalúo real calculado por el servidor, escala 2 (HALF_UP)
 * @throws BadRequestException si el kilataje es 24K, no soportado, o falta el peso
 * @throws ResourceNotFoundException si no existe tabla de precios para la celda
 */
private BigDecimal calcularAvaluoRealAlhaja(PartidaContratoRequest pr, Long plazoId, Integer sucursalId) {
    Integer kilataje = pr.getKilataje();
    if (kilataje == null) {
        throw new BadRequestException("Kilataje es requerido para partidas de tipo ALHAJA");
    }
    if (kilataje == 24) {
        throw new BadRequestException("Oro de 24K no es prendable");
    }
    if (!KILATAJES_COCAE.contains(kilataje)) {
        throw new BadRequestException("Kilataje no soportado: " + kilataje);
    }
    if (pr.getPesoGramos() == null || pr.getPesoGramos().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BadRequestException("Peso en gramos debe ser mayor que cero para partidas ALHAJA");
    }
    String hechura = pr.getHechura(); // ya viene como "F"/"N"/"E" desde el frontend (hechuraCod)
    PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(Math.toIntExact(plazoId), sucursalId, kilataje, hechura);
    PlazoHechuraAlhaja tabla = plazoHechuraAlhajaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "No hay precio configurado para plazo=" + plazoId + ", kilataje=" + kilataje
                    + ", hechura=" + hechura));
    return tabla.getPrecioPrestamo()
            .multiply(pr.getPesoGramos())
            .setScale(2, RoundingMode.HALF_UP);
}
```

Wire it into `buildPartida()` **before** `calcularPrestamoMaximo()` runs, and change `calcularPrestamoMaximo` to accept the already-computed `avaluoReal` as a parameter instead of reading `pr.getAvaluoReal()` internally:

```java
// buildPartida() — replace:
//   BigDecimal prestamoMaximo = calcularPrestamoMaximo(pr, parametro);
//   ...
//   partida.setAvaluoReal(pr.getAvaluoReal());
// with:
BigDecimal avaluoReal = esAlhaja(tipoPrenda)
        ? calcularAvaluoRealAlhaja(pr, plazoId, sucursalId)
        : (pr.getAvaluoReal() != null ? pr.getAvaluoReal() : BigDecimal.ZERO);
BigDecimal prestamoMaximo = calcularPrestamoMaximo(avaluoReal, parametro);
// ... validations against prestamoMaximo unchanged ...
partida.setAvaluoReal(avaluoReal);   // server value, not pr.getAvaluoReal()
```

`calcularPrestamoMaximo(BigDecimal avaluo, PlazoParametro parametro)` — same body as today, just drop the first line that reads `pr.getAvaluoReal()` and take `avaluo` as the parameter instead.

**This directly satisfies D-07 and D-08**: the server computes and stores its own `avaluoReal`/`montoPrestamo` ceiling regardless of what the client's screen showed when the form was opened; no explicit "price changed, please confirm" step is needed because the server was always the source of truth for the ceiling, only now it's *actually enforced*.

**Type gotcha to flag for the planner:** `Plazo.id` is `Long`, `PlazoHechuraAlhajaId.idPlazo` is `Integer` — `Math.toIntExact(plazoId)` is required (will throw `ArithmeticException` only if plazo IDs somehow exceed `Integer.MAX_VALUE`, a non-issue at this scale, but the conversion must be explicit, not silently truncating).

### Rounding contract (D-06 / ORO-04)

Document explicitly (Javadoc block at the top of `recalcularRegistros` or a dedicated comment) — this is the "single documented rounding contract" that Pitfall 2 (milestone PITFALLS.md) calls out as currently missing:

| Step | Formula | Scale | Mode |
|------|---------|-------|------|
| Intermediate: precio por kilate puro | `precioGramoBase / baseKilataje` | 10 (intermediate only, never stored) | HALF_UP |
| Intermediate: %Prestamo → factor | `porcPrestamo / 100` | 10 (intermediate only) | HALF_UP |
| `PlazoHechuraAlhaja.precioBase` | `precioAvaluo × %Prestamo/100` | **4** (stored) | HALF_UP |
| Intermediate: %Aumento → factor | `porcAumento / 100` | 10 (intermediate only) | HALF_UP |
| `PlazoHechuraAlhaja.precioPrestamo` | `precioBase × (1 + %Aumento/100)` | **4** (stored) | HALF_UP |
| `PartidaContrato.avaluoReal` (server-computed) | `precioPrestamo × pesoGramos` | **2** (stored — matches `PartidaContrato` column scale) | HALF_UP |
| `Contrato.montoPrestamo` / totals | sum of partida-level `montoPrestamo` values | **2** | HALF_UP (unchanged, already correct) |

This matches the scale choices already in the schema (`plazo_hechura_alhaja.precio_base`/`precio_prestamo` are `DECIMAL(12,4)`; `partida_contrato.avaluo_real`/`monto_prestamo` are `DECIMAL(18,2)`) — no schema change needed for scale, only the derivation logic changes.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| Storing/looking up the 24-cell COCAE table | A hardcoded `Map<String, BigDecimal>` in Java or a `switch` on kilataje/hechura | The new `oro_tabla_prestamo` table + repository | Business already asked for admin-editability in a *future* phase (D-03) — hardcoding in Java would require a redeploy to ever change a single cell; a table makes that future phase additive, not a rewrite |
| "Does this kilataje exist" checks | Ad-hoc `if (k==6 \|\| k==8 \|\| ...)` chains scattered across methods | A single `static final List<Integer> KILATAJES_COCAE` constant, checked once in `calcularAvaluoRealAlhaja` | D-05 requires a single consistent rejection message; scattering the check risks divergent messages/behavior between the amortización preview (future) and the actual contract creation path |
| BigDecimal percentage math | Manual `double`/`float` arithmetic anywhere in this chain | `BigDecimal` with explicit `.setScale()`/`RoundingMode.HALF_UP`, exactly as the rest of the codebase already does | `double` cannot represent decimal fractions exactly; COCAE parity (D-06) requires cent-exact math, and this project has already standardized on BigDecimal everywhere else in the calc chain |

**Key insight:** Every piece of this phase's "new" logic is a data-lookup + BigDecimal arithmetic problem, not an algorithmic one. The temptation to "clean up" or "simplify" the irregular 24-cell table into a smoother formula (Anti-Pattern 1 in milestone `ARCHITECTURE.md`) is the single biggest risk — resist it explicitly, the table is irregular by design (COCAE's own business rules, not a bug to be smoothed).

## Common Pitfalls

### Pitfall 1: `Plazo.id` (Long) vs `PlazoHechuraAlhajaId.idPlazo` (Integer) mismatch
**What goes wrong:** Passing `plazo.getId()` directly into a `new PlazoHechuraAlhajaId(...)` constructor call fails to compile (type mismatch) or, if cast carelessly, silently truncates for very large IDs.
**Why it happens:** `PlazoHechuraAlhajaId` was designed when plazo IDs were controller-path-variable `Integer`s (`PlazoController` methods take `Integer idPlazo`); `Plazo.id` itself is `Long` (`@GeneratedValue` identity column).
**How to avoid:** Use `Math.toIntExact(plazoId)` explicitly at the one call site in `ContratoService`, not a raw cast.
**Warning signs:** Compile error "incompatible types: Long cannot be converted to Integer" when wiring `calcularAvaluoRealAlhaja`.

### Pitfall 2: 24K rows in the new table return `porc_prestamo = 0.0000`, which could silently produce `precioBase = 0` instead of failing loudly
**What goes wrong:** If the D-04 rejection (kilataje==24 → `BadRequestException`) is not checked *before* the `PlazoHechuraAlhaja` lookup, a 24K partida would compute `avaluoReal = 0.00` (since the imported `%Prestamo` for 24K is 0% in all three hechuras) and then fail with the generic "el préstamo debe ser mayor que cero" message — exactly the confusing failure mode D-04 explicitly asks to avoid.
**Why it happens:** The 0% rows exist in the table for completeness/consistency (so `recalcularRegistros` never hits a missing-cell `ResourceNotFoundException` for 24K), but that's a data-completeness choice, not a signal that 24K should silently resolve to $0.
**How to avoid:** Check `kilataje == 24` as the **first** validation in `calcularAvaluoRealAlhaja`, before any repository lookup — as shown in the code example above.
**Warning signs:** A test asserting `BadRequestException` message for 24K instead gets "el monto de préstamo debe ser mayor que cero" (the pre-existing generic check in `buildPartida`).

### Pitfall 3: Testing `recalcularRegistros` directly is impossible — it's `private`
**What goes wrong:** Attempting `@InjectMocks`-based direct unit tests of the private method won't compile/reflectively-invoke cleanly with the project's existing Mockito test style (see `UsuarioServiceTest.java` — plain constructor injection, no `@InjectMocks`, no reflection tricks used anywhere in the codebase).
**Why it happens:** The method is intentionally private (implementation detail of `PlazoService`).
**How to avoid:** Test through the public entry points — `actualizarTodosPrecios(idPlazo, sucursalId, precioBaseOro)` and `recalcularTodasLasTablas(sucursalId, request, usuario)` — mocking `PlazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(...)`/`findByIdSucursalId(...)` to return known `PlazoHechuraAlhaja` rows and the new `OroTablaPrestamoRepository.findByIdSucursalId(...)` to return the confirmed 24-cell values, then assert on the `PlazoHechuraAlhaja` objects passed to `saveAll(...)` (capture via `ArgumentCaptor`, same pattern already used in `UsuarioServiceTest`).
**Warning signs:** None yet — flagging proactively since this is the first test written against this method.

### Pitfall 4: `hechura` string casing/format mismatch between frontend and `PlazoHechuraAlhajaId`
**What goes wrong:** `PlazoHechuraAlhajaId.hechura` is declared `length = 1` and the confirmed convention is single-char codes `"F"/"N"/"E"`. The frontend's `avaluo.component.ts` already sends `hechuraCod` (confirmed via direct read, line ~583: `hechura: p.hechuraCod`), computed by a `hechuraCodigo()` helper — so this should already be correct end-to-end. **But** `PartidaContratoRequest.hechura` has no `@Pattern`/`@NotBlank` validation today, so a malformed or missing `hechura` for an ALHAJA partida will surface as a confusing `ResourceNotFoundException` ("No hay precio configurado... hechura=null") instead of a clean 400.
**How to avoid:** Consider adding an explicit null/blank check for `hechura` alongside the kilataje checks in `calcularAvaluoRealAlhaja` (`BadRequestException` if blank), rather than relying on the downstream `ResourceNotFoundException` to surface the problem.
**Warning signs:** A test posting a partida with `hechura=null` gets a 404-shaped error instead of a 400.

### Pitfall 5 (inherited from milestone PITFALLS.md — still applies here): BigDecimal `equals()` vs `compareTo()` in tests
**What goes wrong:** `assertEquals(new BigDecimal("1065.4748"), actual)` can fail even when the value is numerically correct, because `BigDecimal.equals()` is scale-sensitive (`10.00` ≠ `10.0`).
**How to avoid:** Always assert with `assertThat(actual.compareTo(expected)).isEqualTo(0)` (AssertJ, already a project test dependency per `UsuarioServiceTest.java`) — never `assertEquals`/`.equals()` on `BigDecimal` in this phase's tests. This is explicit in D-06.

## Code Examples

### Unit test skeleton for COCAE parity (ORO-04, D-06)

```java
// PlazoServiceTest.java — new file, no existing test for this class today
@ExtendWith(MockitoExtension.class)
class PlazoServiceTest {

    @Mock PlazoRepository plazoRepository;
    @Mock PlazoMapper plazoMapper;
    @Mock TipoPrendaService tipoPrendaService;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock PlazoParametroMapper plazoParametroMapper;
    @Mock PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;
    @Mock PlazoHechuraAlhajaMapper plazoHechuraAlhajaMapper;
    @Mock PrecioOroRepository precioOroRepository;
    @Mock OroTablaPrestamoRepository oroTablaPrestamoRepository; // new dependency

    PlazoService plazoService;

    @BeforeEach
    void setUp() {
        plazoService = new PlazoService(plazoRepository, plazoMapper, tipoPrendaService,
                plazoParametroRepository, plazoParametroMapper,
                plazoHechuraAlhajaRepository, plazoHechuraAlhajaMapper,
                precioOroRepository, oroTablaPrestamoRepository);
    }

    @Test
    void actualizarTodosPrecios_21K_Normal_Semanal_coincideConCOCAE() {
        // Arrange: precio base 21K = 1679.50 (confirmado, sucursal Tierra Colorada),
        // celda 21K/Normal = 63.44% (confirmado), plazo Semanal %Aumento = 10% (confirmado)
        PlazoHechuraAlhaja fila21N = new PlazoHechuraAlhaja();
        fila21N.setId(new PlazoHechuraAlhajaId(1, 1, 21, "N"));
        fila21N.setPorcAumento(new BigDecimal("10.0000")); // NO debe sobreescribirse

        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1))
                .thenReturn(List.of(fila21N));
        OroTablaPrestamo celda21N = new OroTablaPrestamo();
        celda21N.setId(new OroTablaPrestamoId(1, 21, "N"));
        celda21N.setPorcPrestamo(new BigDecimal("63.4400"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21N));
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.empty()); // baseKilataje default 24 en la entidad, pero el precio recibido ya está en base 21 en este dataset — ajustar baseKilataje en el request de la prueba

        // Act
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("1679.50")); // baseKilataje=21 vía config real

        // Assert — compareTo, nunca equals() (D-06)
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("1065.4748"))).isEqualTo(0);
        assertThat(resultado.getPrecioPrestamo().compareTo(new BigDecimal("1172.0223"))).isEqualTo(0);
        assertThat(resultado.getPorcAumento().compareTo(new BigDecimal("10.0000"))).isEqualTo(0); // preservado (ORO-02)
    }
}
```

*Note: the exact expected values above (`1065.4748`, `1172.0223`) are computed from the confirmed inputs (`1679.50 × 63.44% = 1065.4748`, `× 1.10 = 1172.02228 → 1172.0223` at scale 4 HALF_UP) using the formula chain from `PROJECT.md`, not copied from an actual COCAE contract printout — before treating this as a passing baseline, cross-check at least 2-3 real printed COCAE contract totals (not just the config-screen table) per the milestone `PITFALLS.md` "Integration Gotchas" guidance, since screen-displayed percentages may be rounded for display versus COCAE's internal stored precision.*

### D-04/D-05 rejection tests

```java
@Test
void calcularAvaluoRealAlhaja_kilataje24K_rechazaConMensajeClaro() {
    PartidaContratoRequest pr = new PartidaContratoRequest();
    pr.setKilataje(24);
    pr.setPesoGramos(new BigDecimal("5.0"));
    pr.setHechura("N");

    BadRequestException ex = assertThrows(BadRequestException.class,
            () -> contratoService.calcularAvaluoRealAlhaja(pr, 1L, 1));
    assertThat(ex.getMessage()).isEqualTo("Oro de 24K no es prendable");
}

@Test
void calcularAvaluoRealAlhaja_kilatajeNoSoportado_rechaza() {
    PartidaContratoRequest pr = new PartidaContratoRequest();
    pr.setKilataje(16); // no está en {6,8,10,12,14,18,21,24}
    pr.setPesoGramos(new BigDecimal("5.0"));
    pr.setHechura("N");

    BadRequestException ex = assertThrows(BadRequestException.class,
            () -> contratoService.calcularAvaluoRealAlhaja(pr, 1L, 1));
    assertThat(ex.getMessage()).isEqualTo("Kilataje no soportado: 16");
}
```

*(`calcularAvaluoRealAlhaja` is private in the design above — either make it package-private for direct testing, matching no strong existing convention either way in this codebase, or test it indirectly through `buildPartida`/`crearContrato` with a full mocked partida request. Package-private is simpler and lower-risk; flag as a planner decision.)*

### Spoofed-`avaluoReal` regression test (Pitfall 1 closure, D-07)

```java
@Test
void crearContrato_partidaAlhaja_ignoraAvaluoRealDelCliente() {
    // Cliente envía un avaluoReal muy alto para intentar inflar el préstamo máximo
    PartidaContratoRequest pr = new PartidaContratoRequest();
    pr.setIdTipoPrenda(1); // ALHAJA
    pr.setKilataje(14);
    pr.setHechura("N");
    pr.setPesoGramos(new BigDecimal("10.0000"));
    pr.setAvaluoReal(new BigDecimal("999999.00")); // valor spoofed — debe ser ignorado
    pr.setMontoPrestamo(new BigDecimal("500.00")); // dentro del máximo real

    // ... mock PlazoHechuraAlhajaRepository.findById(...) para devolver precioPrestamo real de 14K/N ...

    ContratoResponse response = contratoService.crearContrato(request, "cajero1");

    // El avalúo persistido debe ser el calculado por el servidor, NO 999999.00
    assertThat(response.getPartidas().get(0).getAvaluoReal()
            .compareTo(new BigDecimal("999999.00"))).isNotEqualTo(0);
}
```

## Open Questions

1. **¿La tabla `%Prestamo` importada en changeset 012 necesita repetirse manualmente para cada sucursal nueva en el futuro, o debe considerarse parte de la seed data global?**
   - What we know: hoy solo existe la sucursal 1 (Tierra Colorada); D-01/D-02 confirman que la tabla es fija y editable solo vía changeset futuro.
   - What's unclear: si Prestamil llega a operar una segunda sucursal, ¿usa los mismos 24 valores (mismo negocio, misma política de margen) o valores propios? No está en el alcance de este phase resolverlo.
   - Recommendation: dejar `sucursal_id` como parte de la PK (ya así en el diseño propuesto) para no bloquear esa decisión futura, sin construir nada adicional ahora.

2. **¿El plazo "Semanal" (tabla 8) y otros plazos futuros (Quincenal, Mensual) tienen su propio `%Aumento` plano confirmado, o solo Diario (7%) y Semanal (10%) están verificados?**
   - What we know: CONTEXT.md menciona "una tercera tabla vista antes con 3% (plazo no identificado con certeza)".
   - What's unclear: qué plazo corresponde a ese 3%, y si Quincenal/Mensual (si existen como plazos activos) tienen valores confirmados.
   - Recommendation: esto no bloquea el trabajo de este phase (el `porcAumento` por celda ya se preserva tal cual esté cargado hoy en `plazo_hechura_alhaja`, sea cual sea su valor) — solo importa para llenar/validar datos de plazos adicionales, que es responsabilidad de datos, no de código.

## Sources

### Primary (HIGH confidence — direct codebase reads)
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java` — current `recalcularRegistros`, `actualizarTodosPrecios`, `recalcularTodasLasTablas`, `calcularAvaluoContrato`
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/ContratoService.java` — current `buildPartida`, `calcularPrestamoMaximo`, `crearContrato`
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java`, `PlazoHechuraAlhajaId.java`, `PrecioOro.java`, `Plazo.java`, `TipoPrenda.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/request/PartidaContratoRequest.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java`
- `prestamil-backend/src/main/resources/db/changelog/changes/011-oro-sancion-plata.sql`, `db.changelog-master.xml`
- `prestamil-frontend/src/app/prestamil/pages/avaluos/avaluo/avaluo.component.ts` (confirmed `avaluoReal = precioPrestamo × peso` is already the client-side semantics, validating that the server-side recompute must match exactly)
- `prestamil-backend/src/test/java/com/ignis/prestamil/service/UsuarioServiceTest.java` (confirmed project test conventions: JUnit5 + Mockito + AssertJ, plain constructor injection, no `@InjectMocks`)
- `.planning/PROJECT.md`, `.planning/phases/04-.../04-CONTEXT.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`, `.planning/codebase/AVALUOS.md`, `.planning/codebase/CONVENTIONS.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`

No external library research was needed — this phase introduces zero new dependencies (pure JPA entity + Liquibase changeset + BigDecimal service logic, all patterns already established in the codebase).

## Metadata

**Confidence breakdown:**
- Standard stack: N/A — no new libraries; existing Spring Boot/JPA/Liquibase/BigDecimal stack only
- Architecture: HIGH — every integration point verified against current source, not stale docs
- Pitfalls: HIGH for codebase-grounded pitfalls (type mismatch, private-method testability, 24K short-circuit); MEDIUM for the illustrative rounding example values (computed from confirmed inputs, not yet cross-checked against a real COCAE contract printout — flagged explicitly in Code Examples)

**Research date:** 2026-07-03
**Valid until:** No expiry driver (internal codebase research, not third-party API/library versions) — re-verify only if `PlazoService`/`ContratoService` change materially before this phase is implemented
