---
phase: quick-260726-lin
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - prestamil-backend/src/main/resources/db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql
  - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
  - prestamil-backend/src/main/java/com/ignis/prestamil/model/PrecioOro.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/request/PrecioOroRequest.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/response/PrecioOroResponse.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/service/OroTablaPrestamoService.java
  - prestamil-backend/src/test/java/com/ignis/prestamil/service/OroTablaPrestamoServiceTest.java
  - prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
  - prestamil-frontend/src/app/prestamil/core/models/oro-config.model.ts
  - prestamil-frontend/src/app/prestamil/core/services/oro-config.service.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.html
  - .planning/phases/04.1-configuracion-del-oro-admin-ui-para-tabla-de-24-celdas/04.1-CONTEXT.md
  - .planning/REQUIREMENTS.md
  - .planning/PROJECT.md
autonomous: false
requirements:
  - ORO-09

must_haves:
  truths:
    - "La entidad PrecioOro vuelve a tener factorFundir/factorNormal/factorEspecial, persistidos en la tabla precio_oro scoped por sucursal_id"
    - "El changeset 017 agrega las 3 columnas con DEFAULT 100.0000 — las filas existentes de precio_oro quedan en factor neutro, ningun monto vigente cambia"
    - "GET /api/precio-oro devuelve los 3 factores; PUT /api/precio-oro los persiste (null en el request = conservar el valor vigente)"
    - "OroTablaPrestamoService.toOroCeldaResponse multiplica precioPrestamo por el factor de la hechura de la celda: precioAvaluo x %Prestamo/100 x factor/100"
    - "PlazoService.recalcularRegistros aplica el MISMO factor al derivar PlazoHechuraAlhaja.precioBase — el factor llega al monto real que se ofrece en un contrato nuevo"
    - "La columna Precio Prestamo (referencia) de Configuracion del Oro y el precioBase de PlazoHechuraAlhaja coinciden numericamente para la misma celda kilataje/hechura"
    - "Con los 3 factores en 100.0000, tanto el precioPrestamo de GET /api/oro-tabla-prestamo como el precioBase de PlazoHechuraAlhaja son identicos (compareTo == 0) a los de antes de este cambio"
    - "En recalcularTodasLasTablas, los factores del request se aplican en el MISMO recalculo (no en el siguiente) — el upsert de factores ocurre antes de invocar recalcularRegistros"
    - "porcAumento de cada PlazoHechuraAlhaja se preserva sin cambios; el factor solo entra por precioBase (D-10 de Phase 4 intacto)"
    - "Los contratos ya existentes (VIGENTE) no se recalculan retroactivamente — mantienen su monto snapshoteado (D-09 de Phase 4)"
    - "La pantalla Configuracion del Oro muestra 3 inputs editables (Fundir/Normal/Especial) precargados con el valor vigente, guardados junto al precio del gramo con el boton existente"
    - "Tras guardar, la columna Precio Prestamo (referencia) de las 3 pestanas se refresca reflejando los nuevos factores"
    - "04.1-CONTEXT.md registra que D-16/D-17 fueron revertidas parcialmente, con fecha y razon"
  artifacts:
    - path: "prestamil-backend/src/main/resources/db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql"
      provides: "Reinstaura factor_fundir/factor_normal/factor_especial en precio_oro con DEFAULT 100.0000"
      contains: "ADD COLUMN factor_fundir"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/model/PrecioOro.java"
      provides: "Campos factorFundir/factorNormal/factorEspecial (BigDecimal, precision 7 scale 4) + helper estatico compartido factorDeHechura(PrecioOro, String)"
      contains: "factorDeHechura"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/service/OroTablaPrestamoService.java"
      provides: "Aplicacion del factor de hechura sobre precioPrestamo en toOroCeldaResponse"
      contains: "factorDeHechura"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java"
      provides: "Upsert de factores antes del recalculo + factor aplicado dentro de recalcularRegistros al derivar precioBase"
      contains: "factorDeHechura"
    - path: "prestamil-backend/src/test/java/com/ignis/prestamil/service/OroTablaPrestamoServiceTest.java"
      provides: "Cobertura del calculo de la celda con factor neutro (100) y con factor distinto de 100"
      contains: "factorNeutro"
    - path: "prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java"
      provides: "Cobertura de que recalcularRegistros aplica el factor a precioBase (no-regresion con 100 y proporcionalidad con != 100) y preserva porcAumento"
      contains: "factorNormal"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.html"
      provides: "3 inputs de factor por hechura en la tarjeta de encabezado"
      contains: "factorFundirInput"
    - path: ".planning/phases/04.1-configuracion-del-oro-admin-ui-para-tabla-de-24-celdas/04.1-CONTEXT.md"
      provides: "Nota de reversion de D-16/D-17"
      contains: "REVERTIDA"
  key_links:
    - from: "configuracion-oro.component.html"
      to: "OroConfigService.actualizarPrecioGramo"
      via: "ngModel en 3 inputs + boton Guardar y recalcular"
      pattern: "factores"
    - from: "oro-config.service.ts"
      to: "PUT /api/precio-oro"
      via: "http.put con body { precioGramoBase, factorFundir, factorNormal, factorEspecial }"
      pattern: "factorEspecial"
    - from: "PlazoService.recalcularTodasLasTablas"
      to: "precio_oro (columnas factor_*)"
      via: "precio.setFactorX(request.getFactorX()) cuando no es null, ANTES de invocar recalcularRegistros"
      pattern: "setFactorFundir"
    - from: "PlazoService.recalcularRegistros"
      to: "PlazoHechuraAlhaja.precioBase"
      via: "multiply(factorDeHechura(precio, hechura) / 100) con escala intermedia 10 HALF_UP y setScale(4, HALF_UP) final"
      pattern: "factorDeHechura"
    - from: "OroTablaPrestamoService.toOroCeldaResponse"
      to: "OroCeldaResponse.precioPrestamo"
      via: "multiply(factor / 100) con escala intermedia 10 HALF_UP y setScale(4, HALF_UP) final"
      pattern: "factorDeHechura"
---

<objective>
Reinstaurar el factor de ajuste por hechura (Fundir/Normal/Especial), configurable por sucursal, y aplicarlo como multiplicador adicional tanto sobre el **Precio Prestamo** que muestra la pantalla "Configuracion del Oro" como sobre el **precioBase de `PlazoHechuraAlhaja`**, es decir sobre el monto real que se ofrece en un contrato nuevo.

Purpose: El usuario confirmo que este factor SI existe y SI aplica en la operacion real de COCAE. Las decisiones D-16/D-17 de Phase 4.1 lo eliminaron (changeset 013) por haber sido verificado como codigo muerto en ese momento; esa conclusion era correcta sobre el codigo pero incompleta sobre el negocio. Este task devuelve el mecanismo, ahora **configurable** (no con los defaults viejos 90/100/110) y **persistido por sucursal**. En una segunda pregunta explicita el usuario confirmo ademas que el factor **debe afectar el monto real del prestamo**, no solo la pantalla de referencia — por eso se propaga tambien al motor de plazos.

Output: 3 columnas nuevas en `precio_oro` (seed neutro 100.0000), 3 campos en la entidad y DTOs, un helper compartido `PrecioOro.factorDeHechura(...)`, el factor aplicado en `OroTablaPrestamoService.toOroCeldaResponse` **y** en `PlazoService.recalcularRegistros`, 3 inputs editables en la pantalla, y la documentacion de planning actualizada con la reversion.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@CLAUDE.md

@prestamil-backend/src/main/java/com/ignis/prestamil/model/PrecioOro.java
@prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java
@prestamil-backend/src/main/java/com/ignis/prestamil/service/OroTablaPrestamoService.java
@prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
@prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
@prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.ts
@prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.html
</context>

<decisiones_bloqueadas>
Confirmadas por el usuario. **No revisitar, no preguntar de nuevo.**

- **D-A (punto de aplicacion):** El factor es un multiplicador **adicional** encima del resultado ya calculado. Formula final:
  `Precio Prestamo = Precio Avaluo x %Prestamo(kilataje, hechura) / 100 x factor(hechura, sucursal) / 100`
  donde `Precio Avaluo = precioGramo24k / baseKilataje x kilataje` (**sin cambios**) y `%Prestamo` viene de `oro_tabla_prestamo` (**sin cambios**).
- **D-B (no tocar la tabla de 24 celdas):** `oro_tabla_prestamo` y su logica siguen siendo la fuente de verdad verificada contra COCAE. **Prohibido** modificar la tabla, su entidad, su repositorio o la validacion de `actualizarCelda`.
- **D-C (el factor SI llega al monto real del prestamo) — REVISADA 2026-07-26:** el usuario respondio explicitamente que el factor **debe afectar tambien el monto real del prestamo**. Por lo tanto `PlazoService.recalcularRegistros` **SI** aplica el factor al derivar `PlazoHechuraAlhaja.precioBase`:
  `precioBase(kilataje,hechura) = precioAvaluo(kilataje) x %Prestamo(kilataje,hechura)/100 x factor(hechura,sucursal)/100`
  Restricciones dentro de este cambio:
  - `porcAumento` de cada `PlazoHechuraAlhaja` **NO se toca** — sigue siendo especifico de cada plazo (D-10 de Phase 4). El factor entra unicamente por `precioBase`; `precioPrestamo = precioBase x (1 + porcAumento/100)` cambia solo como consecuencia aritmetica.
  - Se respeta el contrato de redondeo **D-06**: escala intermedia 10 HALF_UP, `setScale(4, HALF_UP)` **solo al final**.
  - Los contratos **ya existentes (VIGENTE) no se recalculan retroactivamente** — quedan snapshoteados al momento de su creacion (D-09 de Phase 4). Prohibido tocar esa regla.
- **D-D (scope sucursal):** El factor se persiste en `precio_oro`, que ya tiene `sucursal_id` UNIQUE. **No** se construye selector de sucursal en la UI — se sigue el patron ya aceptado del proyecto (`sucursalId = 1` hardcodeado en el componente, decision de 2026-05-16 en STATE.md).
- **D-E (valor seed):** `100.0000` para las tres hechuras = neutro, sin efecto. **Prohibido inventar valores** (no usar 90/100/110 de la version vieja). El mecanismo debe existir y ser editable; los valores reales los captura el usuario desde la UI cuando tenga las capturas de COCAE. Con el seed neutro, ningun monto vigente cambia al desplegar.
- **D-F (changesets inmutables):** El changeset `013-drop-factores-hechura-precio-oro.sql` **no se edita ni se revierte**. Se agrega uno nuevo: `017-*` (016 es el ultimo ocupado, verificado en `db.changelog-master.xml`).
</decisiones_bloqueadas>

<nota_de_consistencia>
No hay divergencia consciente en este plan. Los dos motores de calculo quedan alineados sobre la misma formula (D-A):

| Consumidor | Campo | Formula |
|---|---|---|
| `OroTablaPrestamoService.toOroCeldaResponse` | `OroCeldaResponse.precioPrestamo` (referencia en pantalla) | `precioAvaluo x %Prestamo/100 x factor/100` |
| `PlazoService.recalcularRegistros` | `PlazoHechuraAlhaja.precioBase` (monto real de contratos nuevos) | `precioAvaluo x %Prestamo/100 x factor/100` |

Para la misma celda kilataje/hechura y el mismo `precioGramo24k`/`baseKilataje`, ambos numeros deben coincidir (`compareTo == 0`). Cualquier divergencia observada es un bug, no un compromiso de alcance.

Unica asimetria intencional (preexistente, no introducida aqui): `PlazoHechuraAlhaja.precioPrestamo` aplica encima el `porcAumento` propio de cada plazo, que la pantalla de referencia no conoce. Eso es correcto por diseno.
</nota_de_consistencia>

<interfaces>
Contratos ya existentes en el codigo — verificados leyendo los archivos. Usar tal cual, no explorar.

`PrecioOro` (entidad, tabla `precio_oro`, `sucursal_id` UNIQUE) — campos actuales:
```java
Integer id; Integer sucursalId;
BigDecimal precioGramo24k;   // @Column precision=12 scale=4, default BigDecimal.ZERO
String calcularSobre;        // default "PRESTAMO"
Integer baseKilataje;        // default 24
LocalDateTime actualizadoEn; // @UpdateTimestamp
String actualizadoPor;
```

`OroTablaPrestamoService` (a modificar) — metodo privado actual:
```java
private OroCeldaResponse toOroCeldaResponse(OroTablaPrestamo celda, BigDecimal precioGramo24k, int baseKilataje)
// precioAvaluo   = precioGramo24k.divide(new BigDecimal(baseKilataje), 10, HALF_UP)
//                                .multiply(new BigDecimal(kilataje)).setScale(4, HALF_UP)
// precioPrestamo = precioAvaluo.multiply(porcPrestamo.divide(CIEN, 10, HALF_UP)).setScale(4, HALF_UP)
// private static final BigDecimal CIEN = new BigDecimal("100");
```
Se invoca desde `getTabla(sucursalId)` (linea ~55) y desde `actualizarCelda(...)` (linea ~97); ambos ya resuelven `PrecioOro precio = precioOroRepository.findBySucursalId(sucursalId).orElse(null)` (puede ser **null**) y derivan `precioGramo24k`/`baseKilataje` con los mismos fallbacks (`BigDecimal.ZERO` y `24`).

`PlazoService` — **firma real** del metodo privado a modificar (leida del archivo):
```java
private void recalcularRegistros(List<PlazoHechuraAlhaja> registros,
                                 BigDecimal precioGramoBase, int baseKilataje,
                                 Integer sucursalId)
```
Cuerpo actual (resumido):
```java
BigDecimal base = new BigDecimal(baseKilataje);
BigDecimal precioPorKilatePuro = precioGramoBase.divide(base, 10, RoundingMode.HALF_UP);
Map<String, BigDecimal> porcPrestamoPorCelda = oroTablaPrestamoRepository.findByIdSucursalId(sucursalId)
        .stream().collect(Collectors.toMap(r -> r.getId().getKilataje() + "-" + r.getId().getHechura(),
                                           OroTablaPrestamo::getPorcPrestamo));
for (PlazoHechuraAlhaja r : registros) {
    String celda = r.getId().getKilataje() + "-" + r.getId().getHechura();
    BigDecimal porcPrestamo = porcPrestamoPorCelda.get(celda);
    if (porcPrestamo == null) { throw new ResourceNotFoundException(...); }
    BigDecimal precioAvaluo = precioPorKilatePuro.multiply(new BigDecimal(r.getId().getKilataje()));
    BigDecimal precioBase = precioAvaluo
            .multiply(porcPrestamo.divide(CIEN, 10, RoundingMode.HALF_UP))
            .setScale(4, RoundingMode.HALF_UP);          // <-- AQUI se inyecta el factor
    r.setPrecioBase(precioBase);
    r.setPrecioPrestamo(precioBase
            .multiply(BigDecimal.ONE.add(r.getPorcAumento().divide(CIEN, 10, RoundingMode.HALF_UP)))
            .setScale(4, RoundingMode.HALF_UP));         // <-- NO tocar (porcAumento, D-10)
}
```

**Los 3 (y unicos) llamadores de `recalcularRegistros`** — los tres ya tienen `PrecioOro precio` en scope:
| Metodo | Linea aprox. | `precio` disponible | Nulabilidad |
|---|---|---|---|
| `actualizarTodosPrecios(idPlazo, sucursalId, precioBaseOro)` | 302-304 | `precioOroRepository.findBySucursalId(sucursalId).orElse(null)` | **puede ser null** |
| `recalcularPrecioBasePorTablaOro(sucursalId)` | 365-370 | `...orElseThrow(...)` | nunca null |
| `recalcularTodasLasTablas(sucursalId, request, usuario)` | 397-406 | `...orElseGet(() -> new PrecioOro())` | nunca null (puede ser transitorio) |

**TRAMPA DE ORDEN en `recalcularTodasLasTablas`** — el metodo hoy hace:
```
1. cargar/crear `precio`            (linea ~397)
2. recalcularRegistros(...) + saveAll   (linea ~404-408)   <-- se recalcula AQUI
3. precio.setPrecioGramo24k(...) etc. + precioOroRepository.save(precio)   (linea ~411-415)
```
Si los factores del request se aplicaran en el paso 3, el recalculo del paso 2 usaria los factores **viejos**. Por eso el upsert de los 3 factores debe ocurrir en el paso 1 (justo despues de cargar/crear `precio`). Los comentarios que ya estan en el codigo ("resolver factores efectivos", "aplicando el factor de hechura") son residuo de la version pre-013 y describen exactamente este orden.

`PlazoHechuraAlhaja` (entidad, **no se modifica**): `PlazoHechuraAlhajaId id` (idPlazo, sucursalId, kilataje, hechura), `Integer tablaPrestamoId`, `BigDecimal precioBase`, `BigDecimal porcAumento`, `BigDecimal precioPrestamo`.

`OroCeldaResponse` (response DTO, **no se modifica**): `kilataje, hechura, hechuraDescripcion, precioAvaluo, porcPrestamo, precioPrestamo, editable`

`OroConfigService` (frontend) — unico consumidor: `configuracion-oro.component.ts`. Firma actual:
```ts
actualizarPrecioGramo(precioGramoBase: number, sucursalId = 1): Observable<PrecioGramoResponse>
// PUT ${apiUrl}/api/precio-oro?sucursalId=N  body { precioGramoBase }
```

Convenciones obligatorias (CLAUDE.md): BigDecimal construido por String, `RoundingMode.HALF_UP`, `.setScale()` explicito, comparar con `compareTo()` nunca `equals()`. Javadoc en espanol en metodos publicos de servicio. Changesets Liquibase SQL-formatted numerados.
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Backend — esquema, entidad, DTOs y helper compartido de los 3 factores</name>
  <files>
    prestamil-backend/src/main/resources/db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql,
    prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml,
    prestamil-backend/src/main/java/com/ignis/prestamil/model/PrecioOro.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/request/PrecioOroRequest.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/response/PrecioOroResponse.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  </files>
  <action>
Esta task es **plumbing puro**: no cambia ningun numero calculado. El cambio de negocio va completo en Task 2.

**1. Changeset `017-restaurar-factores-hechura-precio-oro.sql`** (nuevo). SQL-formatted, autor `emm-a`, id `017-1`. Comentario explicando que revierte 013/D-17 porque el usuario confirmo que el factor SI aplica en COCAE produccion, y que el seed es 100.0000 (neutro) intencionalmente para no alterar montos vigentes:
```sql
ALTER TABLE precio_oro
  ADD COLUMN factor_fundir   DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER base_kilataje,
  ADD COLUMN factor_normal   DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_fundir,
  ADD COLUMN factor_especial DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_normal;
```
Incluir la linea de rollback que hace DROP de las 3 columnas.
`DECIMAL(7,4)` mantiene la convencion de porcentajes del proyecto (`porc_prestamo`, `porc_aumento`) y es identico al tipo que tenian estas columnas en 011. `NOT NULL DEFAULT` hace que las filas existentes queden en 100.0000 sin UPDATE adicional.

**2. `db.changelog-master.xml`:** agregar `<include file="db/changelog/changes/017-restaurar-factores-hechura-precio-oro.sql"/>` despues de la linea de `016-reparar-tabla-prestamo-oro.sql` (verificado: 016 es la ultima).

**3. `PrecioOro.java`:**
- 3 campos nuevos despues de `baseKilataje`, con `@Column(name = "factor_fundir", nullable = false, precision = 7, scale = 4)` (idem normal/especial), inicializados a `new BigDecimal("100.0000")` (construccion por String, convencion del proyecto).
- Constante publica `public static final BigDecimal FACTOR_NEUTRO = new BigDecimal("100.0000");`
- **Helper estatico compartido** (dos servicios lo necesitan — `OroTablaPrestamoService` y `PlazoService` —, por eso vive en la entidad duena de los 3 campos y no duplicado en cada servicio):
```java
/**
 * Devuelve el factor de ajuste configurado para una hechura, tolerante a nulos.
 *
 * @param precio  precio del oro vigente de la sucursal; puede ser null
 * @param hechura clave de hechura ("F", "N" o "E")
 * @return el factor porcentual configurado, o 100.0000 (neutro) si no hay precio,
 *         el factor es null o la hechura es desconocida. Nunca lanza.
 */
public static BigDecimal factorDeHechura(PrecioOro precio, String hechura) { ... }
```
  Implementar con `switch` sobre `"F"/"N"/"E"` (Java 21) y `default -> null`, devolviendo `FACTOR_NEUTRO` cuando el resultado sea null. **Nunca lanzar** por hechura desconocida — las hechuras ya vienen validadas aguas arriba y una excepcion aqui romperia `getTabla`.
- Actualizar el Javadoc de clase mencionando que el factor por hechura es un ajuste configurable por sucursal aplicado sobre el precio de prestamo, tanto en la pantalla Configuracion del Oro como en `PlazoHechuraAlhaja.precioBase`.

**4. `PrecioOroRequest.java`:** 3 campos `BigDecimal factorFundir/factorNormal/factorEspecial`, **nullables** (sin `@NotNull`) con `@DecimalMin("0.0")`. Javadoc: "si viene null se conserva el valor vigente" — mismo criterio ya documentado en `recalcularTodasLasTablas`. No tocar `precioGramoBase` ni sus validaciones.

**5. `PrecioOroResponse.java`:** 3 campos `BigDecimal` correspondientes.

**6. `PlazoService.toPrecioOroResponse`:** mapear los 3 campos nuevos (`r.setFactorFundir(p.getFactorFundir())`, etc.). **No tocar nada mas de `PlazoService` en esta task** — el upsert y el recalculo van en Task 2.
  </action>
  <verify>
    <automated>cd prestamil-backend && ./mvnw -q test</automated>
  </verify>
  <done>
Compila y la suite completa sigue verde **sin cambios en ninguna asercion numerica existente** (los 35 tests previos pasan tal cual — prueba de que esta task no altero ningun calculo). El include de `017-*` esta en `db.changelog-master.xml` despues del `016-*`, y `013-drop-factores-hechura-precio-oro.sql` no tiene diff.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Backend — aplicar el factor en los DOS motores de calculo (pantalla + motor de plazos)</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/service/OroTablaPrestamoService.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java,
    prestamil-backend/src/test/java/com/ignis/prestamil/service/OroTablaPrestamoServiceTest.java,
    prestamil-backend/src/test/java/com/ignis/prestamil/service/PlazoServiceTest.java
  </files>
  <behavior>
Escribir los tests ANTES de la implementacion. Todas las aserciones numericas con `compareTo(new BigDecimal("..."))).isEqualTo(0)`, nunca `equals`.

**A. `OroTablaPrestamoServiceTest` (nuevo archivo).** Patron: `@ExtendWith(MockitoExtension.class)` + `@Mock` de `OroTablaPrestamoRepository`, `PrecioOroRepository` y `PlazoService`, instanciando el servicio en `@BeforeEach` — igual que `PlazoServiceTest`. Reusar el estilo de helpers `buildCelda`/`buildPrecioOro`.

- `getTabla_factorNeutro100_precioPrestamoIdenticoAlCalculoSinFactor`
  precioGramo24k = "2000.0000", baseKilataje = 24, celda 21K/"N" con porcPrestamo = "63.4400", factores 100/100/100
  -> precioAvaluo compareTo "1750.0000" == 0 y precioPrestamo compareTo "1110.2000" == 0. Prueba de no-regresion: mismo numero que antes del cambio.

- `getTabla_factorFundir90_reducePrecioPrestamoDeLaCeldaFundir`
  Misma base, celda 21K/"F" con porcPrestamo = "62.6700", factorFundir = "90.0000"
  -> precioPrestamo compareTo "986.7825" == 0 (1750 x 0.6267 x 0.90).

- `getTabla_seleccionaFactorPorHechura`
  Tres celdas 14K F/N/E con el mismo porcPrestamo y factores 90/100/110
  -> los tres precioPrestamo son distintos y crecientes F < N < E.

- `getTabla_sinPrecioOroConfigurado_usaFactorNeutro`
  `precioOroRepository.findBySucursalId` devuelve `Optional.empty()`
  -> no lanza NPE; precioAvaluo y precioPrestamo compareTo ZERO == 0.

- `actualizarCelda_aplicaFactorEnLaRespuesta`
  `actualizarCelda(1, 14, "N", req)` con factorNormal = "110.0000"
  -> el `OroCeldaResponse` devuelto ya trae el factor aplicado y se verifica `plazoService.recalcularPrecioBasePorTablaOro(1)`.

**B. `PlazoServiceTest` (archivo existente — AGREGAR, no reescribir).** Los 3 tests actuales se conservan intactos: `actualizarTodosPrecios_21K_Normal_coincideConCOCAE` es, de hecho, la prueba de no-regresion mas fuerte (su `buildPrecioOro(21)` construye un `PrecioOro` con los factores en su default 100.0000, asi que sus numeros `1065.4748` / `1172.0223` **deben seguir pasando sin cambios**). Si alguno de esos 3 se rompe, la implementacion esta mal — no ajustar la asercion.

Extender el helper `buildPrecioOro` a una sobrecarga `buildPrecioOro(Integer baseKilataje, String factorF, String factorN, String factorE)` que setee los 3 factores; mantener la firma de 1 argumento delegando con "100.0000" x3.

Nuevos tests (escenario base compartido para que los numeros sean redondos: precioGramoBase = "2400.0000", baseKilataje = 24 -> precioPorKilatePuro = 100; fila 14K/"N" con porcAumento = "10.0000"; celda 14-N con porcPrestamo = "50.0000" -> precioAvaluo = 1400, base sin factor = 700):

- `actualizarTodosPrecios_factorNeutro100_precioBaseSinCambio`
  factores 100/100/100
  -> precioBase compareTo "700.0000" == 0, precioPrestamo compareTo "770.0000" == 0. No-regresion explicita.

- `actualizarTodosPrecios_factorNormal90_reducePrecioBaseProporcionalmente`
  factorNormal = "90.0000"
  -> precioBase compareTo "630.0000" == 0 (700 x 0.90) y precioPrestamo compareTo "693.0000" == 0 (630 x 1.10).
  -> **y** `porcAumento` compareTo "10.0000" == 0: el factor NO contamina el porcAumento propio del plazo (D-10).

- `actualizarTodosPrecios_sinPrecioOroConfigurado_usaFactorNeutro`
  `precioOroRepository.findBySucursalId(1)` devuelve `Optional.empty()`
  -> no lanza NPE; baseKilataje cae al default 24 y precioBase compareTo "700.0000" == 0.

- `recalcularTodasLasTablas_aplicaFactorDelRequestEnElMismoRecalculo`
  **El test que blinda la trampa de orden.** `PrecioOro` vigente en BD con factorNormal = "100.0000"; request con precioGramoBase = "2400.0000" y factorNormal = "90.0000" (los otros dos null).
  -> el `PlazoHechuraAlhaja` capturado en `saveAll` tiene precioBase compareTo "630.0000" == 0. Si sale "700.0000", el upsert quedo despues del recalculo -> bug.
  -> el `PrecioOro` capturado en `precioOroRepository.save` tiene factorNormal compareTo "90.0000" == 0 y factorFundir/factorEspecial compareTo "100.0000" == 0 (los null del request conservan el vigente).
  Nota de mockeo: `when(precioOroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0))` para que `toPrecioOroResponse` no reciba null.

- `recalcularPrecioBasePorTablaOro_usaElFactorPersistido`
  `PrecioOro` vigente con precioGramo24k = "2400.0000", baseKilataje irrelevante (el metodo fuerza 24) y factorNormal = "90.0000"
  -> precioBase del registro capturado compareTo "630.0000" == 0. Confirma que la cascada disparada desde `OroTablaPrestamoService.actualizarCelda` tambien lleva el factor.
  </behavior>
  <action>
**1. `OroTablaPrestamoService.java`** — factor en la pantalla de referencia:
- Cambiar la firma privada a `toOroCeldaResponse(OroTablaPrestamo celda, PrecioOro precio)`, resolviendo dentro `precioGramo24k` (`precio != null ? precio.getPrecioGramo24k() : BigDecimal.ZERO`) y `baseKilataje` (`precio != null && precio.getBaseKilataje() != null ? precio.getBaseKilataje() : 24`) con los mismos fallbacks que hoy tienen `getTabla`/`actualizarCelda`. Ajustar ambos llamadores (`getTabla` linea ~55 y `actualizarCelda` linea ~97), eliminando las variables locales `precioGramo24k`/`baseKilataje` que quedan redundantes.
- Aplicar el factor sobre `precioPrestamo` **despues** del `%Prestamo`, manteniendo el contrato de redondeo D-06 (escala intermedia 10 HALF_UP, `setScale(4, HALF_UP)` solo al final):
```java
BigDecimal precioPrestamo = precioAvaluo
        .multiply(porcPrestamo.divide(CIEN, 10, RoundingMode.HALF_UP))
        .multiply(PrecioOro.factorDeHechura(precio, hechura).divide(CIEN, 10, RoundingMode.HALF_UP))
        .setScale(4, RoundingMode.HALF_UP);
```
- Actualizar el Javadoc de clase y de `getTabla` con la formula completa (D-A) y con la nota de que el mismo factor se aplica en `PlazoService.recalcularRegistros`, por lo que este numero coincide con `PlazoHechuraAlhaja.precioBase`.
- **No tocar** el calculo de `precioAvaluo`, la validacion de 24K en `actualizarCelda`, ni `oroTablaPrestamoRepository`.

**2. `PlazoService.java`** — factor en el motor de plazos (el cambio de negocio nuevo, D-C revisada):

*(a) Firma de `recalcularRegistros`* — agregar el `PrecioOro` como ultimo parametro (nullable):
```java
private void recalcularRegistros(List<PlazoHechuraAlhaja> registros,
                                 BigDecimal precioGramoBase, int baseKilataje,
                                 Integer sucursalId, PrecioOro precio)
```
No re-consultar `precioOroRepository` dentro del metodo: en `recalcularTodasLasTablas` la entidad todavia no esta persistida con los factores nuevos, asi que una re-consulta leeria valores viejos. **Siempre recibir `precio` por parametro.**

*(b) Cuerpo* — unica linea a modificar, la derivacion de `precioBase`:
```java
BigDecimal precioBase = precioAvaluo
        .multiply(porcPrestamo.divide(CIEN, 10, RoundingMode.HALF_UP))
        .multiply(PrecioOro.factorDeHechura(precio, r.getId().getHechura())
                           .divide(CIEN, 10, RoundingMode.HALF_UP))
        .setScale(4, RoundingMode.HALF_UP);
```
**No tocar** la linea de `r.setPrecioPrestamo(...)`: sigue siendo `precioBase x (1 + porcAumento/100)` y `porcAumento` no se lee, escribe ni deriva aqui (D-10).

*(c) Los 3 llamadores* — pasar el `precio` que cada uno ya tiene en scope:
- `actualizarTodosPrecios` (~linea 304): `recalcularRegistros(registros, precioBaseOro, baseKilataje, sucursalId, precio);` — `precio` puede ser null, el helper lo tolera.
- `recalcularPrecioBasePorTablaOro` (~linea 370): `recalcularRegistros(registros, precio.getPrecioGramo24k(), 24, sucursalId, precio);`
- `recalcularTodasLasTablas` (~linea 406): `recalcularRegistros(registros, request.getPrecioGramoBase(), baseKilataje, sucursalId, precio);`

*(d) Orden en `recalcularTodasLasTablas`* — **critico**. Insertar el upsert null-tolerante de los 3 factores en el paso 1, inmediatamente despues del `orElseGet(...)` que carga/crea `precio` y **antes** del bloque que llama `recalcularRegistros`:
```java
// Los factores del request mandan; si vienen nulos se conservan los vigentes.
if (request.getFactorFundir()   != null) precio.setFactorFundir(request.getFactorFundir());
if (request.getFactorNormal()   != null) precio.setFactorNormal(request.getFactorNormal());
if (request.getFactorEspecial() != null) precio.setFactorEspecial(request.getFactorEspecial());
```
Dejar el resto del paso 3 (`setPrecioGramo24k` / `setBaseKilataje` / `setCalcularSobre` / `setActualizadoPor` + `save`) donde esta — esos campos no los lee `recalcularRegistros` (llegan por parametro). Los comentarios ya existentes de los pasos 1 y 2 describen este orden; alinearlos con el codigo final.

*(e) Javadoc* — en `recalcularRegistros` actualizar el Paso 3 de la formula a:
`precioBase(kilate,hechura) = precioAvaluo x %Prestamo(kilate,hechura)/100 x factor(hechura,sucursal)/100`
y anotar que el factor viene de `precio_oro` (configurable por sucursal, neutro = 100) y que **afecta el monto real del prestamo** de contratos nuevos, mientras que los contratos VIGENTE ya emitidos conservan su monto snapshoteado (D-09). En `recalcularTodasLasTablas`, documentar que los factores del request se aplican **en el mismo recalculo**.

**3. Tests** — implementar `<behavior>` A y B. En `PlazoServiceTest` no reescribir los 3 tests existentes.
  </action>
  <verify>
    <automated>cd prestamil-backend && ./mvnw -q -Dtest='OroTablaPrestamoServiceTest+PlazoServiceTest' test && ./mvnw -q test</automated>
  </verify>
  <done>
`OroTablaPrestamoServiceTest` pasa con sus 5 casos, `PlazoServiceTest` pasa con los 3 casos originales **intactos** mas los 5 nuevos, y la suite completa sigue verde con 0 fallos. `grep -n "factorDeHechura" PlazoService.java` devuelve al menos una coincidencia **dentro de `recalcularRegistros`**, y `grep -n "setFactorFundir" PlazoService.java` la devuelve **antes** de la llamada a `recalcularRegistros` en `recalcularTodasLasTablas`.
  </done>
</task>

<task type="auto">
  <name>Task 3: Frontend — 3 inputs de factor por hechura en Configuracion del Oro</name>
  <files>
    prestamil-frontend/src/app/prestamil/core/models/oro-config.model.ts,
    prestamil-frontend/src/app/prestamil/core/services/oro-config.service.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/configuracion-oro/configuracion-oro.component.html
  </files>
  <action>
**1. `oro-config.model.ts`:**
- `PrecioGramoResponse`: agregar `factorFundir: number; factorNormal: number; factorEspecial: number;`
- `PrecioGramoRequest`: agregar los mismos 3 como opcionales (`factorFundir?: number;` etc.).

**2. `oro-config.service.ts`:** cambiar `actualizarPrecioGramo` para aceptar el request completo, manteniendo el mismo endpoint y los mismos params:
```ts
actualizarPrecioGramo(body: PrecioGramoRequest, sucursalId = 1): Observable<PrecioGramoResponse>
```
Enviar `body` tal cual en el `http.put`. Actualizar el JSDoc en espanol. Unico consumidor: `configuracion-oro.component.ts` (ya verificado con grep) — actualizar la llamada ahi.

**3. `configuracion-oro.component.ts`:**
- Nuevo estado: `factores: { F: number | null; N: number | null; E: number | null } = { F: null, N: null, E: null };`
- En `cargarPrecioGramo()`, poblar tambien `this.factores` desde la respuesta (`data?.factorFundir ?? 100`, etc.). En el handler de error dejar los tres en `100` (neutro), igual que hoy deja `precioGramo` en null.
- En `guardarPrecioGramo()`, enviar el objeto completo `{ precioGramoBase: this.precioGramo, factorFundir: this.factores.F, factorNormal: this.factores.N, factorEspecial: this.factores.E }`. Mantener la guarda `if (!this.precioGramo || this.precioGramo <= 0) return;` y agregar rechazo si algun factor es null/negativo/NaN, poniendo `errorMessage` = "Los factores por hechura deben ser mayores o iguales a cero".
- Mantener el `cargarTabla()` que ya se dispara en el `next` — es lo que refresca la columna Precio Prestamo con los factores nuevos.
- Actualizar el mensaje de exito: "Precio del gramo y factores por hechura guardados. Tablas recalculadas."
- Actualizar el comentario de clase mencionando el factor por hechura.

**4. `configuracion-oro.component.html`:** dentro de la tarjeta de encabezado existente (`app-card [hidHeader]="true"`), en el mismo bloque `d-flex align-items-end gap-2 flex-wrap` donde vive el input de precio del gramo, agregar 3 inputs `type="number"` con `[(ngModel)]` + `[ngModelOptions]="{ standalone: true }"`, `min="0"`, `step="0.01"`, etiquetados "Factor Fundir (%)", "Factor Normal (%)", "Factor Especial (%)", con ids `factorFundirInput`/`factorNormalInput`/`factorEspecialInput` y `style="max-width: 130px"` para que no rompan el layout. Van **antes** del boton "Guardar y recalcular" (el mismo boton guarda todo). Agregar un `<small class="text-muted">` bajo el grupo: "Multiplicador adicional aplicado al precio de prestamo de cada hechura. 100% = sin ajuste. Afecta tambien el monto de contratos nuevos."

No cambiar la tabla de 24 celdas, sus pestanas, ni el flujo de edicion inline de `%Prestamo`.
  </action>
  <verify>
    <automated>cd prestamil-frontend && npm run build && npm run lint</automated>
  </verify>
  <done>
`ng build` compila sin errores de tipo (los 3 campos nuevos tipados en el modelo y usados en componente/template) y `ng lint` pasa sin nuevos warnings.
  </done>
</task>

<task type="auto">
  <name>Task 4: Documentar la reversion de D-16/D-17 en planning</name>
  <files>
    .planning/phases/04.1-configuracion-del-oro-admin-ui-para-tabla-de-24-celdas/04.1-CONTEXT.md,
    .planning/REQUIREMENTS.md,
    .planning/PROJECT.md
  </files>
  <action>
**1. `04.1-CONTEXT.md`:** en la seccion `<decisions>`, bajo "Consolidacion de Precio del Oro", agregar inmediatamente despues de D-17 un bloque de reversion. **No borrar D-16 ni D-17** — el registro historico se conserva. Contenido del bloque (titulo exacto `**D-17 REVERTIDA PARCIALMENTE (2026-07-26, quick task 260726-lin):**`):
   - El usuario confirmo que el factor de ajuste por hechura SI existe y SI aplica en la operacion real de COCAE — informacion nueva no disponible durante Phase 4.1.
   - La verificacion de codigo que sustento D-17 era correcta (los factores no participaban en ningun calculo) pero incompleta respecto al negocio.
   - `factorFundir`/`factorNormal`/`factorEspecial` se reinstauran en `PrecioOro`, DTOs y esquema (changeset 017), ahora configurables desde la pantalla Configuracion del Oro y con seed neutro 100.0000 (NO los defaults viejos 90/100/110).
   - Se aplican como multiplicador adicional sobre el precio de prestamo: `precioAvaluo x %Prestamo/100 x factor/100`, **en los dos motores**: `OroTablaPrestamoService.toOroCeldaResponse` (pantalla de referencia) y `PlazoService.recalcularRegistros` (`PlazoHechuraAlhaja.precioBase`, es decir el monto real de contratos nuevos). El usuario confirmo explicitamente que debe afectar el monto real del prestamo.
   - `porcAumento` de cada plazo (D-10) y el snapshot de contratos VIGENTE (D-09) quedan intactos.
   - D-16 (mudar el precio del gramo a la pantalla nueva) sigue vigente; solo cambia la parte de D-16/D-17 que eliminaba los factores. El changeset 013 NO se modifica.

   Ademas, en la seccion `<domain>`, agregar el marcador `(revertido parcialmente — ver D-17 REVERTIDA)` a la frase que dice "eliminar los 3 campos factor de hechura ... que quedaron sin efecto".

**2. `REQUIREMENTS.md`:**
- Cambiar el estado de **ORO-08** en la tabla de trazabilidad de `Complete` a `Superseded (por ORO-09, 2026-07-26)`, y anotar en el texto del requisito que fue revertido.
- Agregar **ORO-09**: "El negocio puede configurar, por sucursal, un factor de ajuste por hechura (Fundir/Normal/Especial) que se aplica como multiplicador adicional sobre el precio de prestamo, tanto en la pantalla Configuracion del Oro como en el monto ofrecido en contratos nuevos (`PlazoHechuraAlhaja.precioBase`); el valor inicial es 100% (neutro) y no altera retroactivamente los contratos ya emitidos." Estado `Complete`, origen `quick 260726-lin`.

**3. `PROJECT.md`:**
- En la linea de `### Validated` correspondiente a Phase 4.1, cambiar el fragmento "factores de hechura muertos eliminados de UI/DTOs/esquema" por "factores de hechura eliminados en 4.1 y **reinstaurados como factor configurable por sucursal** en el quick task 260726-lin (ORO-09)".
- En `## Context`, agregar un bullet tras la cadena de formula confirmada: "**Factor de ajuste por hechura (2026-07-26):** ademas del `%Prestamo` de la tabla de 24 celdas, existe un factor configurable por hechura (Fundir/Normal/Especial) y por sucursal, almacenado en `precio_oro`, con seed neutro 100.00%. **Confirmado por el usuario: SI afecta el monto real del prestamo** — se aplica tanto al Precio Prestamo de la pantalla Configuracion del Oro como a `PlazoHechuraAlhaja.precioBase` (motor de plazos), de modo que ambos coinciden. Los contratos ya emitidos no se recalculan. Pendiente de confirmar con capturas de COCAE unicamente los valores reales por hechura."
  **No dejar como pendiente la pregunta de si el factor debe propagarse al motor de plazos** — quedo resuelta: si se propaga.
- Agregar una fila a `## Key Decisions`: "Reinstaurar el factor por hechura como configurable por sucursal y propagarlo al monto real del prestamo (revierte D-17 de Phase 4.1) | El usuario confirmo que aplica en COCAE produccion y que debe afectar el monto prestado, no solo la pantalla de referencia; se elimina el hardcode 90/100/110 y se hace editable, con seed neutro para no alterar montos vigentes | Pending verification".

No inventar valores de factor en ninguno de los tres documentos.
  </action>
  <verify>
    <automated>grep -l "REVERTIDA" .planning/phases/04.1-configuracion-del-oro-admin-ui-para-tabla-de-24-celdas/04.1-CONTEXT.md; grep -l "ORO-09" .planning/REQUIREMENTS.md; grep -l "260726-lin" .planning/PROJECT.md</automated>
  </verify>
  <done>
Los tres documentos contienen la nota de reversion: `04.1-CONTEXT.md` con el bloque "D-17 REVERTIDA PARCIALMENTE", `REQUIREMENTS.md` con ORO-09 y ORO-08 marcado Superseded, `PROJECT.md` con el bullet de Context y la fila de Key Decisions. Ningun documento deja abierta la pregunta "si el factor debe propagarse al motor de plazos".
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 5: Verificacion humana — propagacion al monto real y valores de COCAE</name>
  <action>
    Pausar la ejecucion y presentar al usuario los pasos de <how-to-verify>. No continuar hasta recibir la senal de reanudacion.
  </action>
  <what-built>
Factor de ajuste por hechura reinstaurado y configurable: 3 columnas en `precio_oro` (seed 100.0000), 3 inputs editables junto al precio del gramo, y el factor aplicado en los DOS motores de calculo — el Precio Prestamo de referencia de la pantalla Configuracion del Oro **y** el `precioBase` de `PlazoHechuraAlhaja`, que es el monto real que se ofrece en un contrato nuevo.
  </what-built>
  <how-to-verify>
1. Aplicar el changeset 017 (`./mvnw liquibase:update -Pdev` o arrancando el backend) y confirmar que `DESCRIBE precio_oro` muestra `factor_fundir`/`factor_normal`/`factor_especial` en 100.0000.
2. Arrancar backend + frontend y abrir Configuracion / Configuracion del Oro.
3. **No-regresion:** con los tres factores en 100, anotar el "Precio Prestamo (referencia)" de 21K Normal, y en Plazos y Periodos anotar el precio base/prestamo de esa misma celda. Ambos deben ser exactamente los mismos valores que mostraban antes de este cambio.
4. Cambiar "Factor Fundir (%)" a 90 y presionar "Guardar y recalcular". La pestana **Fundir** debe bajar ~10% en todas sus filas; **Normal** y **Especial** no deben cambiar.
5. Recargar la pagina (F5): los 3 factores deben conservar el valor guardado.
6. **Propagacion al monto real (lo que cambio en esta revision):**
   a. Abrir **Plazos y Periodos**: las filas de hechura **Fundir** deben haber bajado ~10% respecto al paso 3; Normal y Especial sin cambios.
   b. Para la misma celda kilataje/hechura, el precio base de Plazos y Periodos debe **coincidir** con el "Precio Prestamo (referencia)" de Configuracion del Oro.
   c. Iniciar un contrato **NUEVO** con una prenda de oro de hechura Fundir: el monto ofrecido debe reflejar el factor 90 (~10% menor).
   d. Abrir un contrato **YA EXISTENTE (VIGENTE)** de hechura Fundir: su monto **NO** debe haber cambiado — los contratos emitidos quedan snapshoteados (D-09).
7. **Aportar los valores reales de COCAE** para las 3 hechuras (captura de la ventana legacy) si estan disponibles — el plan NO invento valores por diseno (D-E). Al capturarlos, recordar que ahora si mueven montos de prestamo reales.
  </how-to-verify>
  <resume-signal>Escribe "aprobado" o describe las diferencias encontradas (incluyendo los valores reales de COCAE si los capturaste).</resume-signal>
</task>

</tasks>

<verification>
1. `cd prestamil-backend && ./mvnw -q test` — suite completa verde, incluyendo el nuevo `OroTablaPrestamoServiceTest` y los tests agregados a `PlazoServiceTest`. Los 3 tests originales de `PlazoServiceTest` pasan con sus aserciones numericas sin modificar.
2. `cd prestamil-frontend && npm run build && npm run lint` — sin errores.
3. `grep -n "factorDeHechura\|setFactorFundir" prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java` — debe haber una coincidencia de `factorDeHechura` **dentro de `recalcularRegistros`** (D-C revisada) y las tres de `setFactor*` **antes** de la llamada a `recalcularRegistros` dentro de `recalcularTodasLasTablas` (trampa de orden).
4. `grep -n "porcAumento" prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java` — ninguna coincidencia nueva; el factor no se mezcla con `porcAumento` (D-10).
5. `git -C prestamil-backend diff --stat` no muestra cambios en `OroTablaPrestamo.java`, `OroTablaPrestamoId.java`, `OroTablaPrestamoRepository.java`, `PlazoHechuraAlhaja.java` ni en changesets previos al 017 (D-B, D-F).
6. Los 3 valores seed en el changeset son `100.0000` — ningun 90/110 (D-E).
7. Consistencia entre motores: para la misma celda, el `precioPrestamo` de `OroCeldaResponse` y el `precioBase` de `PlazoHechuraAlhaja` se derivan de la misma expresion (`precioAvaluo x %Prestamo/100 x factor/100`) con el mismo contrato de redondeo D-06.
</verification>

<success_criteria>
- [ ] Changeset 017 creado, incluido en `db.changelog-master.xml`, y 013 intacto
- [ ] `PrecioOro`, `PrecioOroRequest` y `PrecioOroResponse` exponen los 3 factores; el PUT los persiste y el GET los devuelve
- [ ] `PrecioOro.factorDeHechura(precio, hechura)` existe como helper estatico compartido, tolerante a null (precio null, factor null, hechura desconocida -> 100.0000) y nunca lanza
- [ ] `OroTablaPrestamoService.toOroCeldaResponse` multiplica por el factor de la hechura de la celda, con escala intermedia 10 y `setScale(4, HALF_UP)` final
- [ ] `PlazoService.recalcularRegistros` aplica el MISMO factor al derivar `PlazoHechuraAlhaja.precioBase`, con el mismo contrato de redondeo
- [ ] En `recalcularTodasLasTablas`, el upsert de los 3 factores ocurre ANTES de invocar `recalcularRegistros` (los factores del request aplican en el mismo recalculo)
- [ ] `porcAumento` de cada `PlazoHechuraAlhaja` se preserva sin cambios; contratos VIGENTE no se recalculan retroactivamente
- [ ] `OroTablaPrestamoServiceTest` cubre factor neutro (no-regresion), factor != 100, seleccion por hechura y precio nulo
- [ ] `PlazoServiceTest` cubre factor neutro (no-regresion), factor != 100 proporcional, preservacion de `porcAumento`, precio nulo, orden del upsert en `recalcularTodasLasTablas` y cascada desde `recalcularPrecioBasePorTablaOro`; todas las aserciones con `compareTo`
- [ ] La pantalla Configuracion del Oro tiene 3 inputs de factor precargados, guardados con el boton existente, y la tabla se refresca tras guardar
- [ ] `04.1-CONTEXT.md`, `REQUIREMENTS.md` y `PROJECT.md` documentan la reversion de D-16/D-17 con fecha y razon, y registran que el factor SI se propaga al monto real
- [ ] Verificacion humana aprobada (Task 5)
</success_criteria>

<output>
Al terminar, crear `.planning/quick/260726-lin-reinstaurar-factor-de-ajuste-por-hechura/260726-lin-SUMMARY.md`.

Registrar en STATE.md (Decisions): "El factor de ajuste por hechura (Fundir/Normal/Especial, configurable por sucursal en `precio_oro`) se aplica en los DOS motores de calculo: el Precio Prestamo de referencia de Configuracion del Oro y el `PlazoHechuraAlhaja.precioBase` del motor de plazos. Confirmado por el usuario (2026-07-26): SI debe afectar el monto real del prestamo de contratos nuevos. Ambos motores comparten `PrecioOro.factorDeHechura(...)` y deben coincidir numericamente. Los contratos VIGENTE ya emitidos conservan su monto snapshoteado (D-09) y `porcAumento` por plazo no se toca (D-10). Seed neutro 100.0000: al desplegar no cambia ningun monto; pendiente unicamente capturar los valores reales de COCAE — al ingresarlos, se moveran montos de prestamo reales."

Nota de commits: `prestamil-backend` y `prestamil-frontend` son repos git anidados fuera del worktree de `.planning`. Si el commit desde la sesion falla por aislamiento de worktree (ver decisiones de Phase 04.1 en STATE.md), dejar en el SUMMARY los comandos exactos de `git add`/`git commit` para aplicacion manual.
</output>
</content>
</invoke>
