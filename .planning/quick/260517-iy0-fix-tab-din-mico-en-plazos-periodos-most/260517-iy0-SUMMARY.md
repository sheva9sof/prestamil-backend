---
phase: quick-260517-iy0
plan: 01
subsystem: frontend/configuracion/plazos-periodos
tags: [angular, tabs, dynamic, plazos-periodos, tipos-prenda]
dependency_graph:
  requires: [quick-260516-oio]
  provides: [QUICK-260517-IY0-TAB-DINAMICO]
  affects: [plazos-periodos.component.ts, plazos-periodos.component.html]
tech_stack:
  added: []
  patterns: [computed-getter, ngFor-trackBy, ngbNav-dynamic]
key_files:
  created: []
  modified:
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
    - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
decisions:
  - "normalizarNombreTipoPrenda usa normalize('NFD') + regex para producir ids URL-safe estables (Autos/Motos → autos-motos)"
  - "esTipoAlhaja acepta variantes: alhajas, alhaja, joyeria, joyería"
  - "trackByTabId agregado junto a otros trackBy* existentes para estabilidad de ngbNav"
metrics:
  duration: ~15min
  completed_date: 2026-05-17
  tasks_completed: 2 of 2 automated tasks (checkpoint pending human verify)
  files_changed: 2
---

# Phase quick-260517-iy0 Plan 01: Fix Tab Dinámico en Plazos-Periodos Summary

**One-liner:** Tabs dinámicas derivadas de `selectedPlazo.tiposPrenda` — solo la tab Alhajas carga datos; las demás muestran placeholder "Próximamente".

## What Was Built

Refactored `plazos-periodos.component.ts` and `.html` to replace the hardcoded `<li [ngbNavItem]="'alhajas'">` with a dynamic `*ngFor` over a computed `detalleTabs` getter. Each `TipoPrendaRef` on the selected plazo becomes a tab with a normalized id. The Alhajas tab retains its full content (recalculate, table, add form); all other types render an informational placeholder alert.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Computed detalleTabs + helpers + cambiarTab guarded | 6800e1c | plazos-periodos.component.ts |
| 2 | *ngFor tabs in HTML + preserve alhajas content | 6800e1c | plazos-periodos.component.html |
| 3 (checkpoint) | Human verify — 4 scenarios | awaiting | — |

## Deviations from Plan

None — plan executed exactly as written. Task 1 and Task 2 were committed atomically since they are tightly coupled (TS helpers required by the template).

## Known Stubs

- Tabs for non-Alhajas types (Plata, Varios, Autos/Motos) intentionally show a "Próximamente" placeholder. This is a documented, expected placeholder — future plans will implement their detail content.

## Self-Check: PASSED

- `/c/Users/Emm-a/Documents/GitHub/prestamil/prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts` — FOUND
- `/c/Users/Emm-a/Documents/GitHub/prestamil/prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html` — FOUND
- Commit `6800e1c` — FOUND (frontend repo `manu` branch)
- `ng build --configuration=development` — PASSED (clean, no errors)
