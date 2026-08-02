---
status: awaiting_human_verify
trigger: "En el modal de Plazos y Periodos, pestaña de tipo prenda (ej. Alhaja), el botón para editar un parámetro en la tabla no responde al click"
created: 2026-05-20T00:00:00Z
updated: 2026-05-20T07:45:00Z
---

## Current Focus

hypothesis: CAUSA RAÍZ CONFIRMADA — NgbNavOutlet usa ChangeDetectionStrategy.OnPush. El contenido de cada tab se renderiza dentro de NgbNavOutlet vía ngTemplateOutlet. NgbNavOutlet sólo se marca dirty durante transiciones de tab (llama markForCheck() en su subscription a navItemChange$). Después de que el tab Alhaja se activa y la tabla aparece, NgbNavOutlet ya fue chequeado y su dirty flag se limpia. Cuando el usuario da click en el botón editar → iniciarEdicionPrecio() → editandoPrecioBase actualizado → zone tick → ApplicationRef.tick() → CD traversal → NgbNavOutlet NOT dirty → NOT checked → la vista embebida (tabla alhajas) NO se actualiza → usuario ve "sin efecto visual".
test: Mover el contenido de los tabs FUERA de NgbNavContent/NgbNavOutlet — poner directamente en el modal body usando *ngIf sobre activeTab. Así el contenido queda en la vista raíz del modal (siempre chequeada).
expecting: Con el contenido fuera del boundary OnPush de NgbNavOutlet, cualquier cambio en editandoPrecioBase se refleja en el siguiente tick.
next_action: Aplicar fix — reestructurar template para sacar ngbNavContent del loop y reemplazar ngbNavOutlet con divs condicionales.

## Symptoms

expected: Al hacer click en el botón editar de una fila de parámetros (pestaña Alhaja u otro tipo prenda), se debe abrir el formulario de edición inline o modal para modificar los valores del parámetro.
actual: El botón no hace nada — sin efecto visual, sin error visible.
errors: No se mencionan errores en consola (investigar si los hay).
reproduction: Modal Plazos y Periodos → seleccionar un plazo → click Configurar → pestaña tipo prenda (ej. Alhaja) → click en botón editar/modificar de cualquier fila de parámetro.
timeline: Ocurrió después del commit que renombró el campo porcPrestamoSAvaluoReal → porcIncrementoAvaluo en el componente Angular plazos-periodos.

## Eliminated

- hypothesis: El botón editar en la tabla alhajas usa un campo renombrado que falta
  evidence: `iniciarEdicionPrecio` sólo lee `alhaja.precioBase` — sin relación con porcIncrementoAvaluo
  timestamp: 2026-05-20T00:10:00Z

- hypothesis: La función `avaluoPreview()` causa un loop infinito en CD
  evidence: Función es pura (solo lectura), nunca modifica estado
  timestamp: 2026-05-20T06:00:00Z

- hypothesis: El `[(activeId)]` + `(activeIdChange)` crea un loop infinito de tabs
  evidence: ng-bootstrap no re-emite activeIdChange cuando se asigna el mismo valor; el double-set en cambiarTab es redundante pero inocuo
  timestamp: 2026-05-20T06:05:00Z

- hypothesis: Hay un mismatch en los tipos de prenda entre template y parametrosForm
  evidence: Ambos iteran sobre la misma `selectedPlazo.tiposPrenda` — keys consistentes
  timestamp: 2026-05-20T06:10:00Z

## Evidence

- timestamp: 2026-05-20T00:05:00Z
  checked: plazos-periodos.component.ts — estructura completa
  found: El tab "Parámetros" tiene inputs ngModel directamente. El tab dinámico (Alhaja) tiene tabla con botón editar que llama `iniciarEdicionPrecio(a)`. No existe función `editarParametro()`.
  implication: El "botón editar en la tabla" es iniciarEdicionPrecio en el tab Alhaja.

- timestamp: 2026-05-20T00:08:00Z
  checked: cargarParametros() — orden de operaciones
  found: CRITICAL: `isLoadingTab = false` se asignaba en la línea 191, ANTES de poblar `parametrosForm` (líneas 194-213). Si Angular disparaba CD en el momento de la asignación false, el template podía renderizarse con `!isLoadingTab = true` pero `parametrosForm[t.id] = undefined` para todos los tipos.
  implication: Cualquier TypeError en ngModel durante ese CD rompería el ciclo de change detection del componente, haciendo que los botones en la misma vista no respondieran.

- timestamp: 2026-05-20T06:00:00Z
  checked: iniciarEdicionPrecio + Angular CD detectability
  found: La mutación `this.editandoPrecioBase[key] = value` (añadir propiedad a objeto existente) modifica el objeto sin cambiar su referencia. En modo CheckAlways Angular re-evalúa expresiones, pero crear nueva referencia es más idiomático y garantiza la detección.
  implication: Crear nueva referencia con spread operator hace la actualización explícita y confiable.

- timestamp: 2026-05-20T06:10:00Z
  checked: backend PlazoParametroResponse — campo porcIncrementoAvaluo
  found: Mapper correcto. El API responde con el campo renombrado correctamente.
  implication: El problema no está en el backend.

- timestamp: 2026-05-20T07:00:00Z
  checked: ng-bootstrap NgbNavOutlet source — ChangeDetectionStrategy
  found: NgbNavOutlet usa ChangeDetectionStrategy.OnPush. NgbModal adjunta el template del modal como root view via applicationRef.attachView(). Dentro de ese root view, NgbNavOutlet es un componente OnPush hijo. NgbNavOutlet sólo llama markForCheck() durante transiciones de tab en su subscription a navItemChange$. Después de la activación inicial del tab y su primer chequeo, NgbNavOutlet ya no está dirty. Clicks posteriores (iniciarEdicionPrecio) modifican estado de PlazosPeriodosComponent pero NO marcan NgbNavOutlet dirty — el contenido del tab NO se re-chequea.
  implication: CAUSA RAIZ REAL confirmada con código fuente de ng-bootstrap.

- timestamp: 2026-05-20T07:30:00Z
  checked: fix aplicado — reestructuración del template
  found: Se eliminaron todos los ngbNavContent y [ngbNavOutlet]. El contenido de cada tab ahora se renderiza directamente en el modal body con *ngIf sobre activeTab. Se agregó trackByHechura para el loop externo de alhajasPorHechura. Build limpio sin errores.
  implication: El contenido ahora está directamente en la vista raíz del modal (siempre chequeada), eliminando el boundary OnPush de NgbNavOutlet.

## Resolution

root_cause: NgbNavOutlet (ng-bootstrap) usa ChangeDetectionStrategy.OnPush. El contenido de los tabs se renderizaba dentro de NgbNavOutlet via ngTemplateOutlet, quedando en su subtree OnPush. NgbNavOutlet sólo se marca dirty durante transiciones de tab (markForCheck() en su subscription a navItemChange$). Después de cargar la tabla de alhajas, NgbNavOutlet ya fue chequeado y su dirty flag se limpia. Los clicks posteriores en el botón editar (iniciarEdicionPrecio) modificaban editandoPrecioBase correctamente, pero Angular no re-chequeaba NgbNavOutlet — por lo que las expresiones *ngIf en la tabla nunca se re-evaluaban y el usuario no veía ningún cambio visual.
fix: |
  Eliminar ngbNavContent y [ngbNavOutlet] del template. Renderizar el contenido de cada tab directamente en el modal body con *ngIf="activeTab === 'X'" — así el contenido queda en la vista raíz del modal (siempre chequeada por ApplicationRef.tick()) en lugar del subtree OnPush de NgbNavOutlet. Adicionalmente se agregó trackByHechura al *ngFor externo de alhajasPorHechura para evitar recreación innecesaria del DOM.
verification: ng build --configuration development — PASSED (sin errores de compilación)
files_changed:
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
