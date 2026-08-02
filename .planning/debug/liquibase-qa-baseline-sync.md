---
status: awaiting_human_verify
trigger: "mvn liquibase:update -Pqa fails with table/trigger already exists — QA DB had pre-existing schema before Liquibase was introduced"
created: 2026-05-16T00:00:00Z
updated: 2026-05-16T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — QA DB has no DATABASECHANGELOG table. Liquibase starts from scratch and fails on existing objects in changesets 001-003.
test: Bootstrap SQL script written. Inserts real MD5SUM checksums (copied from dev DATABASECHANGELOG) for changesets 001-003 with EXECTYPE=EXECUTED. Then mvn liquibase:update -Pqa runs and only applies 004-006.
expecting: mvn liquibase:update -Pqa reports 10 changesets executed (004 through 006-6) with no errors.
next_action: User runs qa-baseline-bootstrap.sql against QA, then mvn liquibase:update -Pqa, then reports result.

## Symptoms

expected: mvn liquibase:update -Pqa applies only new changesets (004-search-indexes, 005-drop-session-token, 006-plazos-sucursal) that don't exist in QA yet
actual: Liquibase tries to run ALL changesets from scratch, fails on existing tables/triggers
errors: "table/trigger already exists" errors for many objects (tables, trigger set_fecha_direccion)
reproduction: run mvn liquibase:update -Pqa from prestamil-backend/ with DATABASECHANGELOG absent in QA
started: First time running Liquibase against QA. QA schema was created manually/via Spring Boot before Liquibase was adopted (2026-05-15).

## Evidence

- timestamp: 2026-05-16T00:00:00Z
  checked: db.changelog-master.xml
  found: 6 includes in order: 001-initial-schema.sql, 002-initial-data.sql, 003-session-params.sql, 004-search-indexes.sql, 005-drop-session-token-usuarios.sql, 006-plazos-sucursal.sql
  implication: All 6 changesets will be attempted when DATABASECHANGELOG does not exist

- timestamp: 2026-05-16T00:00:00Z
  checked: 001-initial-schema.sql
  found: Two changesets — emmanuel:001-schema (all CREATE TABLE IF NOT EXISTS statements + SPRING_SESSION tables) and emmanuel:001-trigger (CREATE TRIGGER set_fecha_direccion splitStatements:false). All CREATE TABLE use IF NOT EXISTS, but the trigger does NOT — it will fail if the trigger exists.
  implication: 001-schema would silently succeed (IF NOT EXISTS guards it) but 001-trigger will fail with "trigger already exists"

- timestamp: 2026-05-16T00:00:00Z
  checked: 002-initial-data.sql
  found: Single changeset emmanuel:002-data — INSERT statements for all seed data (tipoCatalogo, tipo_prenda, empresa, roles, opciones, configuraciones, parametros_sistema, catalogo, cat_subtipo_prenda, cat_valor_prenda, plazo, sucursal, prenda, plazo_prenda, plazo_hechura_alhaja, plazo_parametro, usuarios, roles_opciones, turnos). No IF NOT EXISTS equivalent — will fail on duplicate key if data exists.
  implication: 002-data will fail with duplicate key errors if seed data already exists in QA

- timestamp: 2026-05-16T00:00:00Z
  checked: 003-session-params.sql
  found: Single changeset emmanuel:003-session-params — two INSERTs into parametros_sistema (id=16, id=17). Same risk as 002.
  implication: 003-session-params will fail with duplicate key if rows 16/17 already exist in QA

- timestamp: 2026-05-16T00:00:00Z
  checked: 004-search-indexes.sql
  found: Single changeset emmanuel:004-fulltext-clientes — ALTER TABLE clientes ADD FULLTEXT INDEX ft_clientes_nombre_completo. NEW object not in original schema.
  implication: This changeset MUST run in QA — it does not exist yet

- timestamp: 2026-05-16T00:00:00Z
  checked: 005-drop-session-token-usuarios.sql
  found: Single changeset emmanuel:005-drop-session-token-usuarios — ALTER TABLE usuarios DROP COLUMN session_token. NEW operation.
  implication: This changeset MUST run in QA — column still exists in original schema

- timestamp: 2026-05-16T00:00:00Z
  checked: 006-plazos-sucursal.sql
  found: 7 changesets (emm-a:006-1 through 006-6) — ADD COLUMN sucursal_id to plazo_parametro + plazo_hechura_alhaja, DROP/RECREATE FK and PK, ADD FK constraints, CREATE INDEX. All NEW objects/modifications.
  implication: All 006 changesets MUST run in QA — none of these columns/constraints exist in original schema

- timestamp: 2026-05-16T00:00:00Z
  checked: pom.xml
  found: Liquibase Maven Plugin 4.27.0 configured in two profiles (dev, qa). QA profile uses liquibase-qa.properties pointing to jdbc:mariadb://10.103.133.1:3306/CasaEmp_DEV
  implication: mvn liquibase:update -Pqa will use that remote DB with credentials admin/$IgnisD3v_2025

- timestamp: 2026-05-16T00:00:00Z
  checked: liquibase-qa.properties
  found: QA DB is 10.103.133.1:3306/CasaEmp_DEV. No DATABASECHANGELOG table exists there.
  implication: changelogSync approach requires either (a) running mvn liquibase:changelogSync -Pqa with a filtered set, or (b) directly inserting rows into DATABASECHANGELOG via SQL

## Eliminated

- hypothesis: Add preConditions onFail=MARK_RAN to all problematic changesets
  evidence: Rejected — modifies changeset checksums, breaking existing dev DATABASECHANGELOG. Would require recalculating or ignoring checksums on dev too.
  timestamp: 2026-05-16T00:00:00Z

- hypothesis: Drop the QA DB and recreate from scratch with Liquibase
  evidence: Rejected — QA has real operational data in turnos, clientes, usuarios, contratos etc. Destructive.
  timestamp: 2026-05-16T00:00:00Z

## Resolution

root_cause: QA DB has the original schema (created before Liquibase was adopted) but has no DATABASECHANGELOG table. When mvn liquibase:update -Pqa runs, Liquibase has no record of what's already been applied, so it tries to execute all changesets from 001 onward. Changesets 001-003 fail because the objects/data they create already exist (trigger, tables, seed rows).

fix: Bootstrap DATABASECHANGELOG in QA by directly inserting rows that tell Liquibase "001, 002, and 003 are already applied — skip them". Then run mvn liquibase:update -Pqa normally to apply only 004, 005, and 006. Implementation: (1) add a tagDatabase changeset after 003 in the master changelog to mark the baseline, (2) provide the SQL INSERT statements the user runs directly against QA to create DATABASECHANGELOG and mark 001-003 (plus the tag) as executed.

verification: awaiting user confirmation that mvn liquibase:update -Pqa succeeds after running bootstrap SQL
files_changed:
  - prestamil-backend/src/main/resources/db/qa-baseline-bootstrap.sql (new — run once against QA before update)
