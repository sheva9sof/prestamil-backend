-- ============================================================
-- QA BASELINE BOOTSTRAP SCRIPT
-- ============================================================
-- Purpose: Tells Liquibase that changesets 001, 002, and 003
--          are already applied in QA (schema was created manually
--          before Liquibase was adopted).
--
-- Run this ONCE against the QA database BEFORE running:
--   mvn liquibase:update -Pqa
--
-- After this script, Liquibase will skip 001/002/003 and only
-- apply 004-search-indexes, 005-drop-session-token, and
-- 006-plazos-sucursal.
--
-- IMPORTANT: Do NOT run this on the dev database — it already
-- has DATABASECHANGELOG populated correctly.
-- ============================================================

-- Step 1: Create the Liquibase tracking tables (identical DDL
--         to what Liquibase 4.27.0 creates automatically).

CREATE TABLE IF NOT EXISTS `DATABASECHANGELOGLOCK` (
  `ID`          int          NOT NULL,
  `LOCKED`      tinyint(1)   NOT NULL,
  `LOCKGRANTED` datetime     DEFAULT NULL,
  `LOCKEDBY`    varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial lock row (unlocked).
INSERT INTO `DATABASECHANGELOGLOCK` (`ID`, `LOCKED`, `LOCKGRANTED`, `LOCKEDBY`)
VALUES (1, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE `ID` = `ID`;   -- idempotent; safe to re-run

CREATE TABLE IF NOT EXISTS `DATABASECHANGELOG` (
  `ID`             varchar(255) NOT NULL,
  `AUTHOR`         varchar(255) NOT NULL,
  `FILENAME`       varchar(255) NOT NULL,
  `DATEEXECUTED`   datetime     NOT NULL,
  `ORDEREXECUTED`  int          NOT NULL,
  `EXECTYPE`       varchar(10)  NOT NULL,
  `MD5SUM`         varchar(35)  DEFAULT NULL,
  `DESCRIPTION`    varchar(255) DEFAULT NULL,
  `COMMENTS`       varchar(255) DEFAULT NULL,
  `TAG`            varchar(255) DEFAULT NULL,
  `LIQUIBASE`      varchar(20)  DEFAULT NULL,
  `CONTEXTS`       varchar(255) DEFAULT NULL,
  `LABELS`         varchar(255) DEFAULT NULL,
  `DEPLOYMENT_ID`  varchar(10)  DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 2: Mark changesets 001-schema, 001-trigger, 002-data, and
--         003-session-params as EXECUTED.
--
-- MD5SUM is intentionally NULL: when Liquibase sees a row with NULL
-- checksum it computes the real value from the changeset and updates
-- the row automatically on the next `liquibase:update` run.
-- This avoids any risk of "checksum mismatch" errors.
--
-- DEPLOYMENT_ID is set to 'qa-baseline' so it is visually distinct
-- from real deployments in your history.

-- INSERT IGNORE skips any row whose ID already exists in DATABASECHANGELOG.
-- Safe to re-run: if a changeset was already recorded (e.g. 001-schema was
-- committed before a later changeset failed), the row is preserved as-is.

INSERT IGNORE INTO `DATABASECHANGELOG`
  (`ID`, `AUTHOR`, `FILENAME`, `DATEEXECUTED`, `ORDEREXECUTED`, `EXECTYPE`,
   `MD5SUM`, `DESCRIPTION`, `COMMENTS`, `TAG`, `LIQUIBASE`, `CONTEXTS`, `LABELS`, `DEPLOYMENT_ID`)
VALUES
  -- 001 — initial schema (tables — all CREATE TABLE IF NOT EXISTS)
  ('001-schema',
   'emmanuel',
   'db/changelog/changes/001-initial-schema.sql',
   NOW(), 1, 'EXECUTED',
   NULL, 'sql', '', NULL, '4.27.0', NULL, NULL, 'qa-baseline'),

  -- 001 — trigger set_fecha_direccion (no IF NOT EXISTS guard, already exists in QA)
  ('001-trigger',
   'emmanuel',
   'db/changelog/changes/001-initial-schema.sql',
   NOW(), 2, 'EXECUTED',
   NULL, 'sql', '', NULL, '4.27.0', NULL, NULL, 'qa-baseline'),

  -- 002 — initial seed data (bare INSERTs — rows already exist in QA)
  ('002-data',
   'emmanuel',
   'db/changelog/changes/002-initial-data.sql',
   NOW(), 3, 'EXECUTED',
   NULL, 'sql', '', NULL, '4.27.0', NULL, NULL, 'qa-baseline'),

  -- 003 — session-timeout parametros_sistema rows (id=16, id=17)
  --
  -- IMPORTANT: Only keep this row if parametros_sistema already has
  -- rows with id=16 and id=17 in QA. If those rows DON'T exist yet,
  -- DELETE this entry so Liquibase runs the changeset for real.
  -- Check with: SELECT id FROM parametros_sistema WHERE id IN (16,17);
  ('003-session-params',
   'emmanuel',
   'db/changelog/changes/003-session-params.sql',
   NOW(), 4, 'EXECUTED',
   NULL, 'sql', '', NULL, '4.27.0', NULL, NULL, 'qa-baseline');

-- Step 3: Verify what was inserted.
SELECT ID, AUTHOR, EXECTYPE, MD5SUM, DEPLOYMENT_ID
FROM DATABASECHANGELOG
ORDER BY ORDEREXECUTED;

-- ============================================================
-- After running this script, execute:
--   cd prestamil-backend
--   mvn liquibase:update -Pqa
--
-- Liquibase will see 001-003 as already applied and will run
-- ONLY: 004-fulltext-clientes, 005-drop-session-token-usuarios,
--       006-1, 006-1b, 006-2, 006-2c, 006-3, 006-4, 006-5, 006-6
-- ============================================================
