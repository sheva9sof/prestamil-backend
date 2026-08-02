---
phase: quick-260519-wxy
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
  - prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
autonomous: false
requirements:
  - QUICK-260519-WXY-01
  - QUICK-260519-WXY-02
  - QUICK-260519-WXY-03

must_haves:
  truths:
    - "calcularAvaluoContrato($1000, porc=50, usaAvaluoReal=true) devuelve $1500.00 (no $500)"
    - "calcularAvaluoContrato($1000, porc=0 ó usaAvaluoReal=false) devuelve $1000.00"
    - "DTOs PlazoParametroRequest y PlazoParametroResponse exponen el campo como porcIncrementoAvaluo (no porcPrestamoSAvaluoReal)"
    - "La entidad PlazoParametro sigue mapeando el campo a la columna DB porc_prestamo_s_avaluo_real (sin migración Liquibase)"
    - "El frontend (modelo, componente y template) usa porcIncrementoAvaluo en toda referencia visible"
    - "Tab 1 de plazos-periodos muestra un preview en vivo: 'Si el préstamo es $X → avalúo en contrato: $Y' con la fórmula montoPrestamo × (1 + porc/100)"
    - "El tooltip del input describe el porcentaje como 'incremento sobre el préstamo' (no descuento)"
  artifacts:
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java"
      provides: "Helper calcularAvaluoContrato(BigDecimal montoPrestamo, PlazoParametro parametro) público"
      contains: "calcularAvaluoContrato"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java"
      provides: "Campo porcIncrementoAvaluo (renombrado desde porcPrestamoSAvaluoReal)"
      contains: "porcIncrementoAvaluo"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java"
      provides: "Campo porcIncrementoAvaluo (renombrado desde porcPrestamoSAvaluoReal)"
      contains: "porcIncrementoAvaluo"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java"
      provides: "Mapeo entity.porcPrestamoSAvaluoReal ↔ dto.porcIncrementoAvaluo en ambas direcciones"
    - path: "prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts"
      provides: "Interfaces PlazoParametroRequest y PlazoParametroResponse con porcIncrementoAvaluo"
      contains: "porcIncrementoAvaluo"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts"
      provides: "Helper avaluoPreview(t) y constante de préstamo de muestra para el preview en Tab 1"
      contains: "porcIncrementoAvaluo"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html"
      provides: "Input porcIncrementoAvaluo con tooltip + label de preview en vivo"
      contains: "porcIncrementoAvaluo"
  key_links:
    - from: "PlazoParametroMapper.toPlazoParametroResponse"
      to: "PlazoParametroResponse.porcIncrementoAvaluo"
      via: "response.setPorcIncrementoAvaluo(entity.getPorcPrestamoSAvaluoReal())"
      pattern: "setPorcIncrementoAvaluo.*getPorcPrestamoSAvaluoReal"
    - from: "PlazoParametroMapper.actualizarDesdeRequest"
      to: "PlazoParametro.porcPrestamoSAvaluoReal"
      via: "entity.setPorcPrestamoSAvaluoReal(request.getPorcIncrementoAvaluo())"
      pattern: "setPorcPrestamoSAvaluoReal.*getPorcIncrementoAvaluo"
    - from: "plazos-periodos.component.html"
      to: "parametrosForm[t.id].porcIncrementoAvaluo"
      via: "[(ngModel)] binding en input de Tab 1"
      pattern: "ngModel.*porcIncrementoAvaluo"
---

<objective>
Corregir la fórmula de cálculo del avalúo real en el backend (PlazoService) y renombrar el campo en la frontera HTTP/UI (DTOs + frontend) sin tocar el schema de DB ni el campo de la entidad JPA.

Purpose: La fórmula actual conceptual (porc% del préstamo como avalúo) producía valores menores al préstamo cuando el cliente quiere lo opuesto: un avalúo de contrato mayor al préstamo. La nomenclatura `porcPrestamoSAvaluoReal` reforzaba la confusión; "porcIncrementoAvaluo" describe el comportamiento correcto.

Output:
- Helper `calcularAvaluoContrato` agregado/corregido en PlazoService con la fórmula `monto × (1 + porc/100)`.
- DTOs y mapper renombrados: campo expuesto al exterior es `porcIncrementoAvaluo`, pero la columna DB y el campo de entidad permanecen sin cambios (`porc_prestamo_s_avaluo_real` / `porcPrestamoSAvaluoReal`).
- Frontend modelo/componente/template renombrados y con preview en vivo + tooltip educativo en Tab 1 (Parámetros).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/STATE.md
@prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
@prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java
@prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java
@prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java
@prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
@prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
@prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
@prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html

<interfaces>
<!-- Entidad JPA — NO cambia. La columna y el campo Java mantienen su nombre. -->

From prestamil-backend/.../model/PlazoParametro.java (UNCHANGED):
```java
@Column(name = "porc_prestamo_s_avaluo_real", nullable = false, precision = 9, scale = 4,
        columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
private BigDecimal porcPrestamoSAvaluoReal = BigDecimal.ZERO;

@Column(name = "usa_avaluo_real", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
private Boolean usaAvaluoReal = false;
```

Helper objetivo (a agregar como método público en PlazoService, importar `java.math.BigDecimal` y `java.math.RoundingMode` ya presentes):
```java
public BigDecimal calcularAvaluoContrato(BigDecimal montoPrestamo, PlazoParametro parametro) {
    if (!parametro.getUsaAvaluoReal()
        || parametro.getPorcPrestamoSAvaluoReal() == null
        || parametro.getPorcPrestamoSAvaluoReal().compareTo(BigDecimal.ZERO) == 0) {
        return montoPrestamo;
    }
    BigDecimal factor = BigDecimal.ONE.add(
        parametro.getPorcPrestamoSAvaluoReal()
            .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
    );
    return montoPrestamo.multiply(factor).setScale(2, RoundingMode.HALF_UP);
}
```

Convención del proyecto (CLAUDE.md):
- Mapper es clase `@Component` con métodos manuales (NO MapStruct en este mapper específico — ver PlazoParametroMapper.java actual).
- DTOs usan `@Getter @Setter` (Lombok).
- Frontend: interfaces sin prefijo `I`, camelCase.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Backend — agregar helper calcularAvaluoContrato y renombrar campo en DTOs/mapper</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
  </files>
  <action>
    Tres cambios atómicos en backend. La entidad `PlazoParametro` y la columna DB `porc_prestamo_s_avaluo_real` NO se tocan.

    **1.1 — PlazoService.java**
    Agregar el método público `calcularAvaluoContrato(BigDecimal montoPrestamo, PlazoParametro parametro)` exactamente como aparece en `<interfaces>`. Colocarlo al final de la clase (después de `actualizarTodosPrecios`), antes del último `}`. Las constantes `FACTOR_TROY_ONZA` y `KILATES_PUROS` ya existen; los imports de `BigDecimal` y `RoundingMode` ya están presentes. Importar `com.ignis.prestamil.model.PlazoParametro` solo si no está ya importado (ya lo está, línea 11). Documentar con Javadoc en español:

    ```java
    /**
     * Calcula el avalúo que aparece en el contrato a partir del monto del préstamo
     * y los parámetros del plazo. Si la sucursal no usa avalúo real (o el porcentaje
     * configurado es cero/nulo), el avalúo del contrato es igual al monto del préstamo.
     *
     * Fórmula: avaluoContrato = montoPrestamo × (1 + porcPrestamoSAvaluoReal / 100)
     *
     * Ejemplo: préstamo $1,000 con 50% → avalúo en contrato $1,500.
     *
     * @param montoPrestamo monto efectivamente prestado al cliente
     * @param parametro     parámetros del plazo/tipo de prenda/sucursal
     * @return avalúo a imprimir en el contrato, con escala 2 (HALF_UP)
     */
    ```

    **1.2 — PlazoParametroRequest.java**
    Renombrar la propiedad `private BigDecimal porcPrestamoSAvaluoReal;` → `private BigDecimal porcIncrementoAvaluo;`. Lombok regenera getters/setters automáticamente. No tocar otros campos.

    **1.3 — PlazoParametroResponse.java**
    Renombrar `private BigDecimal porcPrestamoSAvaluoReal;` → `private BigDecimal porcIncrementoAvaluo;`. No tocar otros campos.

    **1.4 — PlazoParametroMapper.java**
    Tres lugares a actualizar (el mapper es manual, NO MapStruct):
    - En `toPlazoParametroResponse` (línea ~45): cambiar
      `response.setPorcPrestamoSAvaluoReal(plazoParametro.getPorcPrestamoSAvaluoReal());`
      por
      `response.setPorcIncrementoAvaluo(plazoParametro.getPorcPrestamoSAvaluoReal());`
    - En `actualizarDesdeRequest` (línea ~78): cambiar
      `if (request.getPorcPrestamoSAvaluoReal() != null) entity.setPorcPrestamoSAvaluoReal(request.getPorcPrestamoSAvaluoReal());`
      por
      `if (request.getPorcIncrementoAvaluo() != null) entity.setPorcPrestamoSAvaluoReal(request.getPorcIncrementoAvaluo());`
    - En `toPlazoParametro` (línea ~109): cambiar
      `plazoParametro.setPorcPrestamoSAvaluoReal(request.getPorcPrestamoSAvaluoReal() != null ? request.getPorcPrestamoSAvaluoReal() : BigDecimal.ZERO);`
      por
      `plazoParametro.setPorcPrestamoSAvaluoReal(request.getPorcIncrementoAvaluo() != null ? request.getPorcIncrementoAvaluo() : BigDecimal.ZERO);`

    Verificar con grep que NO quede ninguna referencia a `getPorcPrestamoSAvaluoReal()` / `setPorcPrestamoSAvaluoReal()` aplicada al objeto request/response (sí debe quedar aplicada al objeto `entity` o `plazoParametro` porque ese sigue siendo el nombre del campo en la entidad).

    NO crear migración Liquibase. NO renombrar campo en entidad. NO renombrar columna DB.
  </action>
  <verify>
    <automated>cd prestamil-backend && mvn -q -DskipTests compile</automated>
    Compilación exitosa (Lombok genera getters/setters del nuevo nombre, MapStruct no aplica aquí porque el mapper es manual).

    Luego ejecutar grep manualmente:
    - `grep -rn "porcIncrementoAvaluo" prestamil-backend/src/main/java/com/ignis/prestamil/request prestamil-backend/src/main/java/com/ignis/prestamil/response` debe encontrar exactamente una ocurrencia en cada archivo.
    - `grep -rn "getPorcPrestamoSAvaluoReal\|setPorcPrestamoSAvaluoReal" prestamil-backend/src/main/java/com/ignis/prestamil/request prestamil-backend/src/main/java/com/ignis/prestamil/response` debe devolver 0 resultados.
    - `grep -n "calcularAvaluoContrato" prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java` debe encontrar al menos 1 ocurrencia.
  </verify>
  <done>
    - PlazoService compila e incluye el método `calcularAvaluoContrato` con la fórmula `monto × (1 + porc/100)`.
    - PlazoParametroRequest y PlazoParametroResponse exponen `porcIncrementoAvaluo`.
    - PlazoParametroMapper traduce correctamente entre `porcIncrementoAvaluo` (DTO) y `porcPrestamoSAvaluoReal` (entidad).
    - `mvn compile` pasa sin warnings de campos renombrados.
    - DB schema y entidad JPA intactos.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Frontend — renombrar campo en modelo/componente/template y agregar preview en vivo + tooltip en Tab 1</name>
  <files>
    prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  </files>
  <action>
    Tres cambios atómicos en frontend. El template solo afecta el bloque de Tab 1 (Parámetros) donde aparece "% Préstamo s/Avalúo Real" (línea ~143).

    **2.1 — plazo.model.ts**
    Renombrar la propiedad `porcPrestamoSAvaluoReal?: number;` → `porcIncrementoAvaluo?: number;` en la interfaz `PlazoParametroRequest` (línea 22).
    Renombrar `porcPrestamoSAvaluoReal: number;` → `porcIncrementoAvaluo: number;` en la interfaz `PlazoParametroResponse` (línea 74).
    No tocar otros campos.

    **2.2 — plazos-periodos.component.ts**
    - Línea ~204: cambiar `porcPrestamoSAvaluoReal: 0,` → `porcIncrementoAvaluo: 0,` dentro del objeto por defecto de `parametrosForm[t.id]`.
    - Agregar al final de la clase (antes del último `}`) un helper para el preview en vivo:

      ```typescript
      /**
       * Calcula el avalúo de contrato de muestra para el preview en vivo de Tab 1.
       * Usa un préstamo de referencia de $1,000 (configurable vía PREVIEW_PRESTAMO).
       * Fórmula: avaluo = prestamo × (1 + porc / 100). Si usaAvaluoReal=false o porc=0,
       * el avalúo es igual al préstamo.
       *
       * @param tipoPrendaId id del tipo de prenda (clave de parametrosForm)
       * @returns objeto { prestamo, avaluo } con valores numéricos en pesos
       */
      avaluoPreview(tipoPrendaId: number): { prestamo: number; avaluo: number } {
        const prestamo = this.PREVIEW_PRESTAMO;
        const form = this.parametrosForm[tipoPrendaId];
        if (!form || !form.usaAvaluoReal) return { prestamo, avaluo: prestamo };
        const porc = Number(form.porcIncrementoAvaluo ?? 0);
        if (!porc || isNaN(porc)) return { prestamo, avaluo: prestamo };
        const avaluo = prestamo * (1 + porc / 100);
        return { prestamo, avaluo };
      }
      ```

    - Declarar la constante como propiedad readonly de la clase (junto a las otras propiedades al inicio):

      ```typescript
      readonly PREVIEW_PRESTAMO = 1000;
      ```

      Colocar este readonly cerca de las demás propiedades de la clase (no como módulo top-level — Angular component style: campos en la clase).

    **2.3 — plazos-periodos.component.html**
    Reemplazar el bloque del input `porcPrestamoSAvaluoReal` (líneas ~141-155 dentro del `<div class="row g-2 mb-2">` que contiene el label "% Préstamo s/Avalúo Real" y el checkbox "Usa Avalúo Real") por:

    ```html
    <div class="row g-2 mb-2">
      <div class="col-md-4">
        <label class="form-label small mb-0">% Incremento sobre Avalúo</label>
        <input type="number" class="form-control form-control-sm" step="0.0001"
          [(ngModel)]="parametrosForm[t.id].porcIncrementoAvaluo" [ngModelOptions]="{ standalone: true }"
          title="Porcentaje de incremento sobre el préstamo para determinar el valor de referencia que aparece en el contrato. Varía por zona/sucursal." />
        <small class="form-text text-muted">
          Si el préstamo es ${{ avaluoPreview(t.id).prestamo | number:'1.2-2' }}
          → avalúo en contrato: ${{ avaluoPreview(t.id).avaluo | number:'1.2-2' }}
        </small>
      </div>
      <div class="col-md-4 d-flex align-items-end">
        <div class="form-check">
          <input type="checkbox" class="form-check-input"
            [id]="'usa-real-' + t.id"
            [(ngModel)]="parametrosForm[t.id].usaAvaluoReal" [ngModelOptions]="{ standalone: true }" />
          <label class="form-check-label" [for]="'usa-real-' + t.id">Usa Avalúo Real</label>
        </div>
      </div>
    </div>
    ```

    Cambios clave:
    - Label: "% Préstamo s/Avalúo Real" → "% Incremento sobre Avalúo".
    - Atributo `title` en el input = tooltip del task spec (HTML nativo, sin requerir ng-bootstrap tooltip directive — esto evita un nuevo módulo).
    - `<small>` debajo del input renderiza el preview en vivo usando el helper `avaluoPreview(t.id)` y el pipe `number:'1.2-2'` (formato con 2 decimales).
    - Binding: `porcPrestamoSAvaluoReal` → `porcIncrementoAvaluo`.

    Verificar con grep final que NO quede ninguna referencia a `porcPrestamoSAvaluoReal` en `prestamil-frontend/src`.
  </action>
  <verify>
    <automated>cd prestamil-frontend && npx ng build --configuration development --output-hashing=none</automated>
    Build de Angular exitoso, sin errores de TypeScript ni de template.

    Luego ejecutar grep manualmente:
    - `grep -rn "porcIncrementoAvaluo" prestamil-frontend/src` debe encontrar exactamente 5 ocurrencias (2 en plazo.model.ts, 2 en .ts, 1 en .html).
    - `grep -rn "porcPrestamoSAvaluoReal" prestamil-frontend/src` debe devolver 0 resultados.
    - `grep -n "avaluoPreview\|PREVIEW_PRESTAMO" prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts` debe encontrar la declaración del helper y la constante.
  </verify>
  <done>
    - El build de Angular pasa sin errores.
    - No queda ninguna referencia a `porcPrestamoSAvaluoReal` en el frontend.
    - El componente expone el helper `avaluoPreview` y la constante `PREVIEW_PRESTAMO`.
    - El template Tab 1 muestra label "% Incremento sobre Avalúo", tooltip vía `title`, y el preview en vivo "Si el préstamo es $1,000.00 → avalúo en contrato: $X.XX".
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Verificación humana — fórmula corregida en UI y persistencia E2E</name>
  <what-built>
    - Backend: helper `calcularAvaluoContrato` con fórmula `monto × (1 + porc/100)`; DTOs y mapper exponen `porcIncrementoAvaluo` traduciendo a `porcPrestamoSAvaluoReal` en la entidad.
    - Frontend: modelo, componente y template renombrados; preview en vivo + tooltip en Tab 1 (Parámetros) de plazos-periodos.
    - DB y entidad JPA intactas (sin migración).
  </what-built>
  <how-to-verify>
    1. **Levantar backend** (`cd prestamil-backend && mvn spring-boot:run`) y **frontend** (`cd prestamil-frontend && npm start`).
    2. Loguearse y navegar a **Configuración → Plazos y Periodos**.
    3. Seleccionar un plazo que tenga al menos un tipo de prenda asociado y abrir el tab **Parámetros** (Tab 1).
    4. Para uno de los tipos de prenda:
       - Marcar el checkbox **"Usa Avalúo Real"**.
       - Escribir `50` en el input **"% Incremento sobre Avalúo"**.
       - Verificar que aparezca debajo: **"Si el préstamo es $1,000.00 → avalúo en contrato: $1,500.00"**.
       - Hover sobre el input → tooltip nativo del navegador muestra: *"Porcentaje de incremento sobre el préstamo para determinar el valor de referencia que aparece en el contrato. Varía por zona/sucursal."*
    5. Probar más valores rápidos:
       - `0` → preview muestra "$1,000.00 → $1,000.00".
       - `25` → preview muestra "$1,000.00 → $1,250.00".
       - `100` → preview muestra "$1,000.00 → $2,000.00".
       - Desmarcar **"Usa Avalúo Real"** → preview vuelve a "$1,000.00 → $1,000.00" (sin importar el porcentaje).
    6. Click **"Guardar {tipo}"** con un valor de 50% y `usaAvaluoReal=true` marcado. Esperar confirmación de éxito.
    7. **Verificar persistencia**: Recargar la página (F5), volver a Plazos y Periodos, seleccionar el mismo plazo, ir a Parámetros. El input debe mostrar `50` (o `50.0000`) y el checkbox marcado, el preview debe mostrar "$1,500.00".
    8. **Verificar payload de red** (DevTools → Network): el PUT/POST a `/api/plazos/{id}/parametros/...` debe enviar `porcIncrementoAvaluo` en el body (no `porcPrestamoSAvaluoReal`); la respuesta GET subsecuente debe traer `porcIncrementoAvaluo`.
    9. **Verificar DB** (opcional, sólo si tienes acceso a MariaDB): `SELECT porc_prestamo_s_avaluo_real, usa_avaluo_real FROM plazo_parametro WHERE plazo_id={X} AND tipo_prenda_id={Y} AND sucursal_id=1;` — debe mostrar `50.0000` y `1`.
  </how-to-verify>
  <resume-signal>Escribe "approved" si todo funciona; si algo falla, describe el síntoma (preview vacío, payload con nombre viejo, error 400, etc.).</resume-signal>
</task>

</tasks>

<verification>
- Backend: `mvn -q -DskipTests compile` pasa en prestamil-backend.
- Frontend: `npx ng build --configuration development` pasa en prestamil-frontend.
- Grep cruzado:
  - Frontend `src/`: 0 ocurrencias de `porcPrestamoSAvaluoReal`, 5 ocurrencias de `porcIncrementoAvaluo`.
  - Backend `request/` y `response/`: 0 referencias a getters/setters viejos.
  - Backend entidad: campo `porcPrestamoSAvaluoReal` intacto (sigue siendo el nombre Java que mapea a la columna).
- Checkpoint humano confirma:
  - Preview en vivo correcto para los 4 escenarios (50%, 0%, 25%, 100%, checkbox desmarcado).
  - Tooltip visible al hacer hover.
  - Persistencia E2E: guardar 50%, recargar, ver 50% otra vez con preview $1,500.
  - Payload HTTP usa el nuevo nombre.
</verification>

<success_criteria>
- `calcularAvaluoContrato($1000, porc=50, usaAvaluoReal=true)` → `$1,500.00` (validado vía UI preview que usa la misma fórmula y persistencia que confirma el backend acepta el campo).
- `calcularAvaluoContrato($1000, usaAvaluoReal=false)` → `$1,000.00`.
- DTOs y frontend usan `porcIncrementoAvaluo`; entidad y DB sin cambios.
- Tab 1 de plazos-periodos muestra preview en vivo y tooltip.
- Sin migración Liquibase requerida.
- Checkpoint humano aprobado.
</success_criteria>

<output>
After completion, create `.planning/quick/260519-wxy-correcci-n-f-rmula-aval-o-real-calculara/260519-wxy-SUMMARY.md`
</output>
