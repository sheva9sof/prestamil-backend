---
phase: quick-260522-euz
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
autonomous: false
requirements:
  - ADD2-PERIODO-LABEL
  - ADD2-TIPOS-VISIBLES
  - ADD2-TAB2-RAMIFICADO

must_haves:
  truths:
    - "El selector del modal de plazo muestra Diario/Semanal/Quincenal/Mensual (NO un input numérico de días)."
    - "Al elegir Semanal y 12 periodos, la UI muestra '84 días máx.' como duración total del plazo."
    - "Las tabs dinámicas se renderizan SÓLO para Alhajas, Plata y Varios (no para Autos/Motos aunque exista en BD)."
    - "La tab de Alhajas y la tab de Plata muestran la tabla de kilataje/hechura/precio."
    - "La tab de Varios NO muestra tabla de precios; muestra el control 'Usa Avalúo Real' + '% Incremento avalúo' + mensaje del valuador."
    - "La tab de Autos/Motos muestra el mensaje 'no disponible en esta versión' (si por algún motivo se asigna)."
    - "El backend sigue recibiendo diasPorPeriodo como 1/7/15/30 (no se cambia el contrato del API)."
  artifacts:
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts"
      provides: "Helpers de clasificación esTipoPlata/esTipoVarios/esTipoAutoMoto, opciones TIPOS_PERIODO, getters de label de periodo/plazo y filtro de tabs visibles."
      contains: "TIPOS_PERIODO"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html"
      provides: "Select de tipo de período en el modal, etiqueta de duración total, ramificación visual de tab por tipo de prenda."
      contains: "Tipo de período"
  key_links:
    - from: "modal plazoModal (HTML)"
      to: "formData.diasPorPeriodo"
      via: "select [(ngModel)] con opciones 1/7/15/30"
      pattern: "ngValue.*1.*ngValue.*7.*ngValue.*15.*ngValue.*30"
    - from: "detalleTabs getter (TS)"
      to: "esTipoAutoMoto helper"
      via: "filtro: excluir tipos de auto/moto de la lista de tabs visibles"
      pattern: "filter.*esTipoAutoMoto"
    - from: "tab-pane dinámica (HTML)"
      to: "clasificadores esTipoAlhaja/esTipoPlata/esTipoVarios"
      via: "ngSwitch o cascada de ngIf por tab.kind"
      pattern: "tab\\.kind"
---

<objective>
Implementar las reglas del Addendum 2 en `PlazosPeriodosComponent`:

1. **Periodo vs Plazo (label rule):** El selector del modal pasa de "Días por periodo" (input numérico) a "Tipo de período" (select: Diario=1, Semanal=7, Quincenal=15, Mensual=30). El backend sigue almacenando `diasPorPeriodo` como int. En la lista y en el header del modal de detalle se muestra la forma humana del plazo: "Plazo Semanal de 12 periodos = 84 días máx."
2. **Tipos de prenda visibles en pantalla:** Sólo se renderizan tabs para Alhajas, Plata y Varios. Autos/Motos se oculta del set de tabs aunque el plazo lo tenga asignado en BD.
3. **Tab 2 ramificada por tipo:** Alhajas y Plata → tabla kilataje/hechura/precio (ya implementada). Varios → sólo `usaAvaluoReal` + `porcIncrementoAvaluo` + mensaje educativo del valuador. Autos/Motos → mensaje "no disponible en esta versión" (defensivo: por si se asignan en BD).

Purpose: Alinear el componente con el lenguaje y las reglas finales del cliente (Addendum 2), sin tocar el contrato del backend.
Output: Componente reescrito en el HTML y con helpers nuevos en el TS, listo para verificación visual.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md

<!-- Estado actual ya leído por el planner; archivos clave embebidos abajo -->

<interfaces>
<!-- Tipos relevantes del modelo. Extraídos de plazo.model.ts. -->
<!-- El executor NO necesita explorar el codebase. -->

From prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts:
```typescript
export interface PlazoRequest {
  nombre: string;
  diasPorPeriodo: number;       // 1 | 7 | 15 | 30 (Diario/Semanal/Quincenal/Mensual)
  numeroPeriodos: number;
  activo: boolean;
  tiposPrenda: number[];
}

export interface PlazoResponse {
  id: number;
  nombre: string;
  diasPorPeriodo: number;
  numeroPeriodos: number;
  activo: boolean;
  tiposPrenda?: TipoPrendaResponse[];
}

export interface TipoPrendaResponse {
  id: number;
  tipo: string;                  // "Alhajas" | "Plata" | "Varios" | "Autos" | "Motos" | ...
}

export interface PlazoParametroRequest {
  porcInteres?: number; porcAlmacen?: number; porcGastosAdmin?: number;
  cat?: number; numMaxRefrendos?: number; porcPrestamoSAvaluo?: number;
  usaAvaluoReal?: boolean; porcIncrementoAvaluo?: number;
  diasGraciaSinInteres?: number; diasAntesPaseVenta?: number;
  importeMinPrestamo?: number;
  // ... más campos opcionales (ver modelo)
}
```
</interfaces>

<current_state>
<!-- Estado vigente del componente. NO se reescribe desde cero — se modifica. -->

- TS file: `plazos-periodos.component.ts` (~590 líneas). Ya tiene:
  - `esTipoAlhaja(tipo)` — clasificador por nombre normalizado (`alhajas|alhaja|joyeria|joyería`).
  - `detalleTabs` getter — mapea TODOS los `selectedPlazo.tiposPrenda` a tabs.
  - `cargarParametros()` con pre-poblado de `parametrosForm` y `usaAvaluoReal`/`porcIncrementoAvaluo` ya presentes.
  - `cargarAlhajas()`, `agregarAlhaja()`, `inicializarTablaEstandar()`, `guardarPrecioBase()`, `recalcularTodo()`.

- HTML file: `plazos-periodos.component.html` (~447 líneas). Ya tiene:
  - Modal `#plazoModal` con `<input type="number" [(ngModel)]="formData.diasPorPeriodo" min="1" />` (línea ~30) — ESTE ES EL CAMBIO.
  - `<ng-container *ngFor="let tab of detalleTabs">` con `*ngIf="tab.isAlhajas; else placeholderTabContent"` (línea ~216) — la rama del else es donde Varios y Autos/Motos colapsan hoy.
  - Lista de plazos con `{{ plazo.diasPorPeriodo }} días × {{ plazo.numeroPeriodos }} periodos`.

- Decisión vigente (STATE.md 2026-05-16): `sucursalId` hardcodeado a 1 — NO tocar.
- Decisión vigente (STATE.md 2026-05-19): `porcIncrementoAvaluo` con preview en vivo `avaluoPreview(t.id)` y tooltip educativo — NO tocar; reusar en la tab Varios.
</current_state>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Helpers de TS — período, plazo y clasificación por tipo de prenda</name>
  <files>prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts</files>
  <action>
Agregar al componente (sin tocar lógica existente de guardado/HTTP):

1. **Constante de tipos de período**, expuesta al template:
```typescript
readonly TIPOS_PERIODO = [
  { dias: 1,  label: 'Diario' },
  { dias: 7,  label: 'Semanal' },
  { dias: 15, label: 'Quincenal' },
  { dias: 30, label: 'Mensual' }
];
```

2. **Helpers de clasificación** (junto a `esTipoAlhaja`, mismo patrón de normalización: trim + lowercase + NFD). Aceptan `{ tipo?: string } | null | undefined` y devuelven boolean:
   - `esTipoPlata(t)` → match exacto: `'plata'`.
   - `esTipoVarios(t)` → match: `'varios'` o nombre que empiece con `vario` (defensivo: `'varios electronicos'`, etc.).
   - `esTipoAutoMoto(t)` → match: `'autos' | 'auto' | 'motos' | 'moto' | 'automoviles' | 'vehiculos'`.

3. **Clasificador unificado `tipoPrendaKind(t)`** que devuelve `'alhaja' | 'plata' | 'varios' | 'auto-moto' | 'otro'` consumido por el HTML para `ngSwitch`. Implementar como:
```typescript
tipoPrendaKind(t: { tipo?: string } | null | undefined): 'alhaja' | 'plata' | 'varios' | 'auto-moto' | 'otro' {
  if (this.esTipoAlhaja(t))   return 'alhaja';
  if (this.esTipoPlata(t))    return 'plata';
  if (this.esTipoVarios(t))   return 'varios';
  if (this.esTipoAutoMoto(t)) return 'auto-moto';
  return 'otro';
}
```
   - `esTipoPlata`, `esTipoVarios`, `esTipoAutoMoto` y `tipoPrendaKind` van como `public`/sin modificador (para uso en template).
   - `esTipoAlhaja` ya existe como `private`; cambiar a sin modificador para que `tipoPrendaKind` no rompa visibilidad cuando se invoque desde otros sitios — no es estrictamente necesario porque `tipoPrendaKind` está en la misma clase, pero verifica que siga compilando.

4. **Reemplazar el getter `detalleTabs`** para que:
   - Excluya tipos `auto-moto` de la lista visible (regla: "DO NOT show price table config this iteration" — equivalente a no mostrar la tab).
   - Devuelva un campo `kind: 'alhaja' | 'plata' | 'varios' | 'otro'` en lugar del booleano `isAlhajas` (mantén `isAlhajas` calculado a partir de `kind === 'alhaja' || kind === 'plata'` para no romper `isAlhajasTab()` u otros consumidores; ese sigue funcionando como "muestra tabla de precios kilataje").
```typescript
get detalleTabs(): Array<{ id: string; label: string; kind: 'alhaja' | 'plata' | 'varios' | 'otro'; isAlhajas: boolean }> {
  return (this.selectedPlazo?.tiposPrenda ?? [])
    .filter(t => !this.esTipoAutoMoto(t))
    .map(t => {
      const kind = this.tipoPrendaKind(t);
      return {
        id: this.normalizarNombreTipoPrenda(t),
        label: t.tipo,
        kind: kind === 'auto-moto' ? 'otro' : kind, // defensivo: ya filtramos arriba
        isAlhajas: kind === 'alhaja' || kind === 'plata' // ambos usan tabla kilataje/hechura
      };
    });
}
```

5. **Helpers para etiquetas humanas del plazo**, expuestos al template:
```typescript
getLabelPeriodo(dias: number | null | undefined): string {
  const d = Number(dias);
  const found = this.TIPOS_PERIODO.find(p => p.dias === d);
  return found?.label ?? (d ? `${d} días` : '—');
}

/** "Plazo Semanal de 12 periodos = 84 días máx." */
getLabelPlazoCompleto(dias: number | null | undefined, periodos: number | null | undefined): string {
  const d = Number(dias) || 0;
  const n = Number(periodos) || 0;
  const periodoLabel = this.getLabelPeriodo(d);
  const total = d * n;
  return `Plazo ${periodoLabel} de ${n} periodos = ${total} días máx.`;
}
```

6. **NO tocar** la lógica de `cargarParametros()`, `guardarParametro()`, `cargarAlhajas()`, `agregarAlhaja()`, `inicializarTablaEstandar()`, `guardarPrecioBase()`, `recalcularTodo()`, ni el contrato del backend.
  </action>
  <verify>
    <automated>cd prestamil-frontend && npx tsc --noEmit -p tsconfig.json 2>&1 | grep -E "(plazos-periodos\\.component\\.ts|error TS)" | head -20</automated>
  </verify>
  <done>El TS compila sin errores. Los nuevos helpers (`TIPOS_PERIODO`, `esTipoPlata`, `esTipoVarios`, `esTipoAutoMoto`, `tipoPrendaKind`, `getLabelPeriodo`, `getLabelPlazoCompleto`) existen y son accesibles desde el template. `detalleTabs` ahora filtra auto-moto y expone `kind`. Ningún otro método existente se elimina o renombra.</done>
</task>

<task type="auto">
  <name>Task 2: HTML — select de tipo de período, etiquetas humanas y ramificación de tab 2</name>
  <files>prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html</files>
  <action>
Modificar el HTML en tres bloques. NO reescribir el archivo completo: aplicar ediciones quirúrgicas.

**Bloque A — Modal de creación/edición de plazo (alrededor de las líneas 28-35):**

Reemplazar el campo input numérico de `diasPorPeriodo` por un `<select>` con las cuatro opciones del Addendum 2. Cambiar también el label del campo:

```html
<div class="col-md-3 mb-3">
  <label class="form-label" for="plazoTipoPeriodo">Tipo de período</label>
  <select id="plazoTipoPeriodo" class="form-select"
          [(ngModel)]="formData.diasPorPeriodo"
          [ngModelOptions]="{ standalone: true }">
    <option *ngFor="let p of TIPOS_PERIODO" [ngValue]="p.dias">{{ p.label }}</option>
  </select>
</div>
```

Justo debajo del segundo input (`plazoNumPeriodos`), agregar una pequeña etiqueta calculada con la duración total del plazo (sólo si ambos valores están presentes):

```html
<div class="col-12 mb-2">
  <small class="text-muted">
    <i class="feather icon-clock me-1"></i>
    {{ getLabelPlazoCompleto(formData.diasPorPeriodo, formData.numeroPeriodos) }}
  </small>
</div>
```

**Bloque B — Header del modal de detalle (alrededor de la línea 76) y lista principal de plazos (alrededor de la línea 410):**

En el header del modal `#detalleModal`, reemplazar:
```html
<small class="text-muted">{{ selectedPlazo?.diasPorPeriodo }} días × {{ selectedPlazo?.numeroPeriodos }} periodos</small>
```
Por:
```html
<small class="text-muted">{{ getLabelPlazoCompleto(selectedPlazo?.diasPorPeriodo, selectedPlazo?.numeroPeriodos) }}</small>
```

En la lista principal (item del `list-group-item`), reemplazar:
```html
<small class="text-muted">{{ plazo.diasPorPeriodo }} días × {{ plazo.numeroPeriodos }} periodos</small>
```
Por:
```html
<small class="text-muted">{{ getLabelPlazoCompleto(plazo.diasPorPeriodo, plazo.numeroPeriodos) }}</small>
```

**Bloque C — Ramificación de tab 2 por tipo de prenda (alrededor de las líneas 212-374):**

El bloque actual usa `*ngIf="tab.isAlhajas; else placeholderTabContent"` para mostrar tabla kilataje O un placeholder genérico. Reemplazar la lógica por una cascada explícita por `tab.kind`. Estructura objetivo (dentro del `<div class="tab-pane" *ngIf="activeTab === tab.id">`):

```html
<div class="pt-3" [ngSwitch]="tab.kind">

  <!-- ALHAJA o PLATA → tabla kilataje/hechura/precio (mantener bloque completo existente) -->
  <ng-container *ngSwitchCase="'alhaja'">
    <ng-container *ngTemplateOutlet="tablaKilatajeTpl; context: { $implicit: tab }"></ng-container>
  </ng-container>
  <ng-container *ngSwitchCase="'plata'">
    <ng-container *ngTemplateOutlet="tablaKilatajeTpl; context: { $implicit: tab }"></ng-container>
  </ng-container>

  <!-- VARIOS → sólo usaAvaluoReal + porcIncrementoAvaluo + mensaje -->
  <ng-container *ngSwitchCase="'varios'">
    <div class="alert alert-info small mb-3" role="alert">
      <i class="feather icon-info me-1"></i>
      Para electrónicos el valuador captura el préstamo manualmente. Este porcentaje determina el avalúo que aparece en el contrato.
    </div>

    <ng-container *ngIf="parametrosForm[getTipoIdFromTab(tab.id)] as form; else variosLoading">
      <div class="row g-2 mb-2">
        <div class="col-md-4 d-flex align-items-end">
          <div class="form-check">
            <input type="checkbox" class="form-check-input"
              [id]="'usa-real-varios-' + tab.id"
              [(ngModel)]="form.usaAvaluoReal" [ngModelOptions]="{ standalone: true }" />
            <label class="form-check-label" [for]="'usa-real-varios-' + tab.id">Usa Avalúo Real</label>
          </div>
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-0">% Incremento avalúo</label>
          <input type="number" class="form-control form-control-sm" step="0.0001"
            [(ngModel)]="form.porcIncrementoAvaluo" [ngModelOptions]="{ standalone: true }" />
          <small class="form-text text-muted">
            Si el préstamo es ${{ avaluoPreview(getTipoIdFromTab(tab.id)).prestamo | number:'1.2-2' }}
            → avalúo en contrato: ${{ avaluoPreview(getTipoIdFromTab(tab.id)).avaluo | number:'1.2-2' }}
          </small>
        </div>
        <div class="col-md-4 d-flex align-items-end">
          <button class="btn btn-sm btn-primary" (click)="guardarParametro(getTipoIdFromTab(tab.id))"
            [disabled]="savingParam[getTipoIdFromTab(tab.id)]">
            <span *ngIf="savingParam[getTipoIdFromTab(tab.id)]" class="spinner-border spinner-border-sm me-1"></span>
            Guardar {{ tab.label }}
          </button>
        </div>
      </div>
      <span *ngIf="paramSaveSuccess[getTipoIdFromTab(tab.id)]" class="text-success small">
        <i class="feather icon-check"></i> Guardado
      </span>
      <span *ngIf="paramSaveError[getTipoIdFromTab(tab.id)]" class="text-danger small">
        {{ paramSaveError[getTipoIdFromTab(tab.id)] }}
      </span>
    </ng-container>
    <ng-template #variosLoading>
      <div class="text-muted small">Cargando parámetros...</div>
    </ng-template>
  </ng-container>

  <!-- AUTO-MOTO → mensaje (defensivo; ya filtramos en detalleTabs, pero por si acaso) -->
  <ng-container *ngSwitchDefault>
    <div class="alert alert-warning small mb-0" role="alert">
      <i class="feather icon-alert-triangle me-1"></i>
      Configuración de tabla de precios para {{ tab.label }} no disponible en esta versión.
    </div>
  </ng-container>

</div>
```

Mover el bloque actual de "Recálculo masivo + spinner + tabla 3 columnas + agregar alhaja" (líneas ~217-360) a un `<ng-template #tablaKilatajeTpl let-tab>` AL FINAL del archivo HTML (junto a los otros `<ng-template>` del modal), para que se pueda reutilizar desde `*ngSwitchCase="'alhaja'"` y `*ngSwitchCase="'plata'"`. NO duplicar el contenido. NO eliminar el bloque de "Sin alhajas: botón inicializar".

**Bloque D — Helper de mapeo tab.id → tipoPrendaId:**

Como `parametrosForm` está indexado por `tipoPrendaId` (numérico) pero el template trabaja con `tab.id` (string normalizado), agregar al TS en Task 1 (si no se hizo) un helper auxiliar:

```typescript
getTipoIdFromTab(tabId: string): number {
  if (!this.selectedPlazo) return 0;
  const found = (this.selectedPlazo.tiposPrenda ?? [])
    .find(t => this.normalizarNombreTipoPrenda(t) === tabId);
  return found?.id ?? 0;
}
```
(`normalizarNombreTipoPrenda` es `private` — cambiar a sin modificador o mantener private y exponer sólo `getTipoIdFromTab` que es público — preferible la segunda.)

**No tocar:**
- El toast de éxito (líneas 5-13).
- El modal `plazoModal` salvo los cambios listados en Bloque A.
- La tab "Parámetros" (líneas 101-209) — ya está bien.
- El bloque de tabla kilataje que se mueve al template — debe quedar funcionalmente IDÉNTICO, sólo dentro del `<ng-template #tablaKilatajeTpl>`.
- Los `trackBy*` ni los `cambiarTab()`.
  </action>
  <verify>
    <automated>cd prestamil-frontend && npx ng build --configuration=development 2>&1 | tail -30</automated>
  </verify>
  <done>El proyecto compila sin errores de Angular. El modal de creación muestra el `<select>` de tipo de período con 4 opciones. El header del modal de detalle y los items de la lista muestran "Plazo Semanal de 12 periodos = 84 días máx." (o el equivalente según valores). Las tabs ALHAJA y PLATA muestran la tabla kilataje (reutilizada vía `ng-template`). La tab VARIOS muestra el mensaje del valuador + checkbox `usaAvaluoReal` + input `% Incremento avalúo` + botón Guardar. La tab AUTO-MOTO (si llegara a aparecer) muestra el mensaje "no disponible". No hay regresiones en el guardado de parámetros ni en la tabla de alhajas.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Verificación visual de las reglas Addendum 2</name>
  <what-built>
- Selector "Tipo de período" en el modal de crear/editar plazo, con opciones Diario/Semanal/Quincenal/Mensual.
- Etiqueta calculada "Plazo {Período} de {N} periodos = {días} días máx." en el modal de creación, header del modal de detalle, y lista de plazos.
- Tab 2 ramificada: Alhajas y Plata → misma tabla kilataje/hechura/precio. Varios → sólo `usaAvaluoReal` + `% Incremento avalúo` + mensaje del valuador. Autos/Motos → tab oculta (o mensaje "no disponible" defensivo).
  </what-built>
  <how-to-verify>
1. Arrancar el frontend: `cd prestamil-frontend && npx ng serve` (backend ya debe estar arriba).
2. Login → ir a Configuración → Plazos y periodos.
3. **Modal Nuevo:**
   - Click en "Nuevo". Verificar que el campo dice "Tipo de período" (NO "Días por periodo") y es un `<select>` con cuatro opciones: Diario, Semanal, Quincenal, Mensual.
   - Seleccionar "Semanal", poner Nº periodos = 12. Confirmar que aparece "Plazo Semanal de 12 periodos = 84 días máx." debajo de los campos.
   - Cambiar a "Mensual" con 6 periodos → debe mostrar "Plazo Mensual de 6 periodos = 180 días máx.".
   - Cancelar (no guardar — la verificación es visual).
4. **Lista principal:**
   - Verificar que cada plazo existente muestra la nueva etiqueta humana ("Plazo Semanal de 12 periodos = 84 días máx." o equivalente según sus datos), NO la vieja "84 días × 12 periodos".
5. **Modal de detalle (Configurar):**
   - Click en "Configurar" de un plazo que tenga tipos Alhajas Y Varios asignados. Si no existe, crea/edita uno para que los tenga.
   - Verificar que el header del modal muestra la nueva etiqueta humana.
   - Tab "Alhajas" → debe verse la tabla kilataje/hechura/precio idéntica a antes (recálculo masivo, 3 columnas Fina/Normal/Especial, agregar alhaja).
   - Tab "Plata" (si existe ese tipo en BD) → debe verse la MISMA tabla kilataje/hechura/precio que Alhajas.
   - Tab "Varios" → debe mostrar:
     * Mensaje informativo: "Para electrónicos el valuador captura el préstamo manualmente. Este porcentaje determina el avalúo que aparece en el contrato."
     * Checkbox "Usa Avalúo Real".
     * Input "% Incremento avalúo".
     * Botón "Guardar Varios".
     * NO debe mostrar la tabla kilataje.
6. **Autos/Motos (si el plazo lo tiene asignado):**
   - Confirmar que la tab NO se renderiza en la barra de tabs (filtrada en `detalleTabs`).
   - Si por algún motivo se llega a un `tab.kind === 'otro'`, debe verse el mensaje "Configuración de tabla de precios para {tipo} no disponible en esta versión".
7. **No-regresiones:**
   - Editar un plazo existente, cambiar el tipo de período, guardar. Confirmar que persiste y se refleja en la lista.
   - En la tab Alhajas, editar el precio base de un kilataje → debe guardar correctamente (sin cambios respecto a comportamiento previo).
   - En la tab Parámetros (la primera tab), editar `% Interés` de cualquier tipo → debe seguir guardando.
  </how-to-verify>
  <resume-signal>Escribir "aprobado" si todo se ve bien, o describir cualquier desviación (texto distinto, tab que falta, layout roto).</resume-signal>
</task>

</tasks>

<verification>
- TS compila (`npx tsc --noEmit`) sin errores nuevos.
- Build Angular en development pasa.
- Verificación humana confirma las tres reglas del Addendum 2.
</verification>

<success_criteria>
- El selector "Tipo de período" reemplaza al input numérico y guarda `diasPorPeriodo` como 1/7/15/30.
- La etiqueta "Plazo {Período} de {N} periodos = {días} días máx." aparece en los tres lugares (modal creación, header detalle, lista).
- Las tabs visibles son sólo Alhajas/Plata/Varios; Autos/Motos no aparece.
- Tab Alhajas y tab Plata renderizan la misma tabla kilataje; Tab Varios renderiza sólo el formulario de avalúo + mensaje.
- No hay regresiones en guardado de parámetros ni en CRUD de alhajas.
</success_criteria>

<output>
Después de completar las tres tasks, crear `.planning/quick/260522-euz-implementar-plazosperiodoscomponent-con-/260522-euz-SUMMARY.md` con:
- Cambios aplicados al TS (lista de helpers nuevos y cambios al getter `detalleTabs`).
- Cambios aplicados al HTML (Bloques A/B/C/D).
- Resultado de la verificación humana (Task 3) — copiado del resume-signal.
- Pendientes / próximos pasos (e.g., extender a Plata si el tipo no existe aún en BD `tipo_prenda`).
</output>
