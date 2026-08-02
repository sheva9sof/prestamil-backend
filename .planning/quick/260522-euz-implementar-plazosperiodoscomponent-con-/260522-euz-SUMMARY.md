---
phase: quick-260522-euz
plan: 01
subsystem: frontend/configuracion/plazos-periodos
tags: [addendum-2, plazos, ui, tipo-periodo, tab-ramificada]
dependency_graph:
  requires: [quick-260519-wxy]
  provides: [TIPOS_PERIODO-select, getLabelPlazoCompleto, tab-kind-ramification]
  affects: [plazos-periodos.component.ts, plazos-periodos.component.html]
tech_stack:
  patterns: [ngSwitch, ngTemplateOutlet, ngValue-select, helper-methods]
key_files:
  modified:
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
decisions:
  - Backend contract unchanged — diasPorPeriodo still stored as int (1/7/15/30)
  - Autos/Motos filtered from detalleTabs at getter level; defensive SwitchDefault handles edge cases
  - tablaKilatajeTpl moved to ng-template for reuse by both Alhaja and Plata tabs
metrics:
  duration: ~5 minutes
  completed: 2026-05-22
  tasks_completed: 2
  tasks_total: 3
---

# Quick Task 260522-euz: PlazosPeriodosComponent — Addendum 2 Rules Summary

**One-liner:** Select de Diario/Semanal/Quincenal/Mensual reemplaza input numérico, etiqueta "Plazo Semanal de 12 periodos = 84 días máx." en 3 lugares, y tab 2 ramificada por kind (alhaja/plata/varios/otro).

## Tasks Executed

### Task 1: Helpers de TS (DONE)
**Commit:** `c1959c3`

Changes to `plazos-periodos.component.ts`:

1. **`readonly TIPOS_PERIODO`** — constant array `[{dias:1,label:'Diario'}, {dias:7,...}, {dias:15,...}, {dias:30,...}]` exposed to template.
2. **`getLabelPeriodo(dias)`** — returns 'Diario'/'Semanal'/'Quincenal'/'Mensual' or fallback `{n} días`.
3. **`getLabelPlazoCompleto(dias, periodos)`** — returns `"Plazo Semanal de 12 periodos = 84 días máx."`.
4. **`esTipoPlata(t)`** — normalized match on `'plata'`.
5. **`esTipoVarios(t)`** — normalized match on `'varios'` or `startsWith('vario')`.
6. **`esTipoAutoMoto(t)`** — normalized match on `['autos','auto','motos','moto','automoviles','vehiculos']`.
7. **`tipoPrendaKind(t)`** — returns `'alhaja' | 'plata' | 'varios' | 'auto-moto' | 'otro'`.
8. **`getTipoIdFromTab(tabId)`** — maps normalized tab id string back to numeric tipoPrendaId for `parametrosForm` lookup.
9. **`detalleTabs` getter rewritten** — filters out auto-moto types, exposes `kind` field alongside `isAlhajas` (backward compatible).
10. **`esTipoAlhaja` and `normalizarNombreTipoPrenda`** — visibility changed from `private` to no-modifier for template accessibility.

### Task 2: HTML — Bloques A/B/C (DONE)
**Commit:** `66c336d`

**Bloque A — Modal creación/edición:**
- Replaced `<input type="number" id="plazoDias">` (label "Días por periodo") with `<select id="plazoTipoPeriodo">` (label "Tipo de período") bound to `formData.diasPorPeriodo` via `[ngValue]` + `*ngFor="let p of TIPOS_PERIODO"`.
- Added duration preview row below: `{{ getLabelPlazoCompleto(formData.diasPorPeriodo, formData.numeroPeriodos) }}`.

**Bloque B — Etiquetas humanas:**
- detalleModal header: replaced `{{ selectedPlazo?.diasPorPeriodo }} días × {{ selectedPlazo?.numeroPeriodos }} periodos` with `{{ getLabelPlazoCompleto(...) }}`.
- List item: replaced `{{ plazo.diasPorPeriodo }} días × {{ plazo.numeroPeriodos }} periodos` with `{{ getLabelPlazoCompleto(plazo.diasPorPeriodo, plazo.numeroPeriodos) }}`.

**Bloque C — Tab 2 ramificada:**
- Replaced `*ngIf="tab.isAlhajas; else placeholderTabContent"` + generic placeholder template with `[ngSwitch]="tab.kind"`.
- `*ngSwitchCase="'alhaja'"` and `*ngSwitchCase="'plata'"` → both use `*ngTemplateOutlet="tablaKilatajeTpl"` (same kilataje table, no duplication).
- `*ngSwitchCase="'varios'"` → alert message + checkbox `usaAvaluoReal` + input `porcIncrementoAvaluo` + `avaluoPreview()` feedback + Guardar button.
- `*ngSwitchDefault` → defensive `alert-warning` "no disponible en esta versión".
- **`ng-template #tablaKilatajeTpl`** added at end of file — contains: recálculo masivo input+button, spinner, error, "sin alhajas" + initialize button, 3-column kilataje table, agregar alhaja form.

**Bloque D — getTipoIdFromTab helper:**
- Added in Task 1 (TS), consumed in Bloque C Varios tab for `parametrosForm` indexing.

## Verification Results

### Automated
- `npx tsc --noEmit` — no errors in `plazos-periodos.component.ts` (pre-existing spec file errors unrelated).
- `npx ng build --configuration=development` — BUILD SUCCESS. Bundle generated in 6148ms.

### Human Verification (Task 3)
**Status: PENDING — awaiting checkpoint:human-verify**

The human verification checkpoint (Task 3) was not executed as per constraints. The verifier should:
1. Open the modal "Nuevo" — confirm the "Tipo de período" select with 4 options (Diario/Semanal/Quincenal/Mensual).
2. Select Semanal + 12 periodos — confirm "Plazo Semanal de 12 periodos = 84 días máx." appears.
3. Confirm list items show new label format.
4. Open "Configurar" on a plazo with Alhajas + Varios — confirm tab Alhajas shows kilataje table, tab Varios shows valuador form.
5. Confirm Autos/Motos tab does NOT appear in the nav.
6. Verify no regressions in saving parameters or alhajas.

## Deviations from Plan

None — plan executed exactly as written. `getTipoIdFromTab` was specified in Bloque D to potentially be added in Task 1, which was done as intended.

## Known Stubs

None — all data is wired to real backend calls (`parametrosForm`, `avaluoPreview`, `guardarParametro`). The Varios tab form is fully functional and reuses the same save mechanism as the Parámetros tab.

## Pending / Next Steps

- If `Plata` type does not yet exist in `tipo_prenda` table in the DB, the tab will never render. A future Liquibase changeset may be needed to seed it.
- Autos/Motos tab configuration is intentionally deferred; the defensive `*ngSwitchDefault` message handles it if the type ever gets assigned to a plazo.
- Human verification (Task 3) must be completed by the developer before merging.

## Self-Check: PASSED

- `c1959c3` exists in frontend repo git log.
- `66c336d` exists in frontend repo git log.
- `plazos-periodos.component.ts` — modified with all required helpers.
- `plazos-periodos.component.html` — modified with all 4 blocks.
- Angular build passed without errors.
