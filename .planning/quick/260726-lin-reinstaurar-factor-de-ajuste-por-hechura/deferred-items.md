# Deferred Items — quick task 260726-lin

Items discovered during execution that are out of scope for this task (not caused by
its changes). Logged per the executor's scope-boundary rule instead of being fixed.

## Frontend build fails on pre-existing SCSS budget errors (unrelated components)

`cd prestamil-frontend && npm run build` exits with code 1 because of two
pre-existing component style budgets exceeded:

- `src/app/prestamil/pages/catalogos/empresas/empresas.component.scss` (4.67 kB vs 4.00 kB max)
- `src/app/prestamil/pages/configuracion/sucursal/sucursal.component.scss` (4.99 kB vs 4.00 kB max)

Verified pre-existing by stashing this task's changes and rebuilding: the same
two errors and exit code 1 occur with a clean `prestamil-frontend` working tree
(baseline commit `559e6a9`). Neither file was touched by this task (Task 3 only
modified `oro-config.model.ts`, `oro-config.service.ts`, and
`configuracion-oro.component.ts/.html`). `configuracion-oro.component.scss` does
not appear in the budget warning/error list, so the new factor inputs did not
push that component over budget.

## Frontend lint has 108 pre-existing errors (unrelated files)

`cd prestamil-frontend && npm run lint` reports 108 errors baseline and after
this task's changes — identical count with or without the Task 3 diff (verified
via `git stash`/`git stash pop`). The 4 errors inside
`configuracion-oro.component.ts` (`_removed`/`_removedSaving` unused vars, lines
~134/136/142/151) are inside `guardarCelda`/`cancelarEdicion`, methods this task
did not modify (pre-existing destructuring pattern from Phase 4.1).

**Recommendation:** address the SCSS budget overruns and the unused-var/`any`
lint backlog in a dedicated cleanup task — out of scope here since neither
issue is caused by the factor-de-hechura change.
