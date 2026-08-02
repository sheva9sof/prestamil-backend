---
phase: quick-260514-x0j
plan: 01
subsystem: database-schema
tags: [liquibase, fulltext, mariadb, clientes, search]
dependency_graph:
  requires: []
  provides: [ft_clientes_nombre_completo index]
  affects: [ClienteRepository, Phase 1 FULLTEXT Search]
tech_stack:
  added: []
  patterns: [Liquibase formatted SQL with rollback directive]
key_files:
  created:
    - prestamil-backend/src/main/resources/db/changelog/changes/004-search-indexes.sql
  modified:
    - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
decisions:
  - "No index on contratos: table does not exist in current schema (001-initial-schema.sql verified)"
  - "No index on telefono: already covered by UNIQUE KEY defined in 001-initial-schema.sql line 305"
  - "Column names in snake_case (apellido_paterno, apellido_materno) as verified against 001-initial-schema.sql lines 297-298"
  - "FULLTEXT IN BOOLEAN MODE chosen (per STATE.md decision) — NATURAL LANGUAGE MODE silences common surnames"
metrics:
  duration: "~5 minutes"
  completed: "2026-05-15"
  tasks_completed: 2
  files_changed: 2
---

# Quick Task 260514-x0j: Agregar Changeset Liquibase con Indice FULLTEXT Summary

**One-liner:** Liquibase changeset 004 adds FULLTEXT INDEX `ft_clientes_nombre_completo` on `clientes(nombre, apellido_paterno, apellido_materno)` for future MATCH/AGAINST search queries.

## What Was Done

Created the Liquibase schema migration required to enable full-text search on the `clientes` table. This is a prerequisite for Phase 1's MATCH/AGAINST query implementation.

### Files Created

**`prestamil-backend/src/main/resources/db/changelog/changes/004-search-indexes.sql`**
- Liquibase formatted SQL changeset with id `004-fulltext-clientes`
- Adds `FULLTEXT INDEX ft_clientes_nombre_completo` on columns `(nombre, apellido_paterno, apellido_materno)`
- Includes `--rollback` directive: `ALTER TABLE clientes DROP INDEX ft_clientes_nombre_completo`
- Engine: InnoDB (already configured on the `clientes` table — no ALTER ENGINE needed)

### Files Modified

**`prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml`**
- Added `<include file="db/changelog/changes/004-search-indexes.sql"/>` after the `003-session-params.sql` entry
- Maintains sequential execution order: 001 → 002 → 003 → 004
- Single entry (no duplicates), consistent 4-space indentation, XML structure unchanged

## Decisions Made

1. **No index on `contratos`:** The `contratos` table does not exist in the current schema (verified by reading `001-initial-schema.sql` in full). Creating an `ALTER TABLE contratos ADD INDEX ...` would cause Liquibase to fail with "Table 'contratos' doesn't exist" and leave the changeset in an invalid state.

2. **No index on `telefono`:** `001-initial-schema.sql` (line 305) already defines `UNIQUE KEY telefono (telefono)`, which serves as a B-tree index. Adding a second index would generate a duplicate warning.

3. **Column names in snake_case:** `apellido_paterno` and `apellido_materno` (not camelCase) — verified against `001-initial-schema.sql` lines 297–298 as specified in the plan.

4. **Rollback directive included:** The `--rollback ALTER TABLE clientes DROP INDEX ft_clientes_nombre_completo;` line enables `liquibase rollback` to revert the changeset, following the project's reversibility standard.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `87b034d` | chore(quick-260514-x0j-01): create Liquibase changeset 004 with FULLTEXT index on clientes |
| Task 2 | `2651d78` | chore(quick-260514-x0j-01): register changeset 004 in db.changelog-master.xml |

## Deviations from Plan

None — plan executed exactly as written. Both tasks completed without modification.

## Functional Verification (Pending)

The following verifications require a running MariaDB instance and are out of scope for this automated execution:

1. **Application startup:** `mvn spring-boot:run` should log `Running Changeset: db/changelog/changes/004-search-indexes.sql::004-fulltext-clientes::emmanuel` without `ChangeSetExecutionFailed`.

2. **Index existence:** `SHOW INDEX FROM clientes WHERE Key_name = 'ft_clientes_nombre_completo';` should return 3 rows with `Index_type = FULLTEXT`.

3. **Existing endpoint regression:** `GET /api/clientes/search?q=garcia` should still return 200 with LIKE-based results (no code changes were made).

## Known Stubs

None — this is a pure schema migration task. No application code stubs introduced.

## Next Step

Refactor `ClienteRepository.searchByNombreCompletoOrTelefono` (or equivalent search method) to use:

```java
@Query(value = "SELECT c FROM Cliente c WHERE " +
    "MATCH(c.nombre, c.apellidoPaterno, c.apellidoMaterno) AGAINST (:q IN BOOLEAN MODE) " +
    "OR c.telefono LIKE :telQuery")
```

This is the scope of Phase 1's main implementation plan. The FULLTEXT index created in this task is the required prerequisite for that query to perform well.

## Self-Check: PASSED

- `004-search-indexes.sql` exists at `prestamil-backend/src/main/resources/db/changelog/changes/` — FOUND
- `db.changelog-master.xml` contains exactly one include for `004-search-indexes.sql` after `003` — FOUND
- Commit `87b034d` exists — FOUND
- Commit `2651d78` exists — FOUND
