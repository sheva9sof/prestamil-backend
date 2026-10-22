--liquibase formatted sql

-- ============================================================================
-- D-09 (plan 07-oct) - Catalogo de prendas estandarizado + descripcion
-- especifica obligatoria en la partida. Revierte la decision del changeset 024.
--
-- Ningun registro se borra. Los que hoy no cumplen se corrigen con un valor
-- provisional y quedan con cat_valor_prenda.pendiente_revision = 1 para que
-- Sistemas los complete desde Catalogos > Prendas (al editarlos se apaga la
-- marca).
--
-- Diagnostico para correr ANTES de aplicar en cada ambiente:
--   -- claves vacias o duplicadas (misma clave sin importar mayusculas/espacios)
--   SELECT v.id_valor_atributo, v.clave, v.descripcion
--   FROM cat_valor_prenda v
--   WHERE v.clave IS NULL OR TRIM(v.clave) = ''
--      OR UPPER(TRIM(v.clave)) IN (SELECT UPPER(TRIM(clave)) FROM cat_valor_prenda
--                                  GROUP BY UPPER(TRIM(clave)) HAVING COUNT(*) > 1);
--   -- sin descripcion general
--   SELECT id_valor_atributo, clave FROM cat_valor_prenda
--   WHERE descripcion IS NULL OR TRIM(descripcion) = '';
--   -- partidas cuya descripcion especifica no llega a 10 caracteres
--   SELECT id, id_contrato, num_partida, descripcion FROM partida_contrato
--   WHERE CHAR_LENGTH(TRIM(descripcion)) < 10;
--
-- Diagnostico en dev (CasaEmp_DEV, 2026-10-08):
--   - claves duplicadas: Cadena (ids 4 y 13), Anillo (ids 5 y 11). Se conserva
--     la del id menor; la otra pasa a "Cadena-13" / "Anillo-11".
--   - sin descripcion: ids 9 Esclava, 10 Collar, 11 Anillo, 12 Dige, 13 Cadena.
--   - partidas con descripcion < 10 caracteres: ids 1-7, 9, 14, 21. Se dejan
--     intactas (son contratos ya firmados); la regla aplica a partidas nuevas.
-- ============================================================================

--changeset emm-a:041-1
--comment: D-09 - Marca de registros del catalogo corregidos por esta migracion y pendientes de que Sistemas los complete.
ALTER TABLE cat_valor_prenda
  ADD COLUMN pendiente_revision TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'D-09: clave o descripcion provisional; Sistemas debe completarla';
--rollback ALTER TABLE cat_valor_prenda DROP COLUMN pendiente_revision;

--changeset emm-a:041-2
--comment: D-09 - Descripcion general del catalogo copiada a la partida al empenar (snapshot, igual que clave_prenda). La descripcion especifica sigue en partida_contrato.descripcion.
ALTER TABLE partida_contrato
  ADD COLUMN descripcion_general VARCHAR(100) NULL COMMENT 'D-09: cat_valor_prenda.descripcion al momento del empeno' AFTER clave_prenda;
--rollback ALTER TABLE partida_contrato DROP COLUMN descripcion_general;

--changeset emm-a:041-3
--comment: D-09 - Backfill de partidas historicas con la clave y descripcion vigentes del catalogo, ANTES de renombrar duplicados y de poner descripciones provisionales, para no congelar valores provisionales en contratos firmados.
UPDATE partida_contrato p
JOIN cat_valor_prenda v ON v.id_valor_atributo = p.id_valor_prenda
SET p.descripcion_general = TRIM(v.descripcion)
WHERE p.descripcion_general IS NULL
  AND v.descripcion IS NOT NULL AND TRIM(v.descripcion) <> '';
UPDATE partida_contrato p
JOIN cat_valor_prenda v ON v.id_valor_atributo = p.id_valor_prenda
SET p.clave_prenda = TRIM(v.clave)
WHERE (p.clave_prenda IS NULL OR TRIM(p.clave_prenda) = '')
  AND v.clave IS NOT NULL AND TRIM(v.clave) <> '';
--rollback UPDATE partida_contrato SET descripcion_general = NULL;

--changeset emm-a:041-4
--comment: D-09 - Claves normalizadas: sin espacios en los extremos y, si faltan, una provisional SIN-CLAVE-<id> marcada para revision.
UPDATE cat_valor_prenda SET clave = TRIM(clave) WHERE clave IS NOT NULL AND clave <> TRIM(clave);
UPDATE cat_valor_prenda
SET clave = CONCAT('SIN-CLAVE-', id_valor_atributo), pendiente_revision = 1
WHERE clave IS NULL OR clave = '';
--rollback UPDATE cat_valor_prenda SET clave = NULL WHERE clave = CONCAT('SIN-CLAVE-', id_valor_atributo);

--changeset emm-a:041-5
--comment: D-09 - Claves duplicadas: conserva la del id menor y renombra las demas a <clave>-<id> (truncada a 20). La comparacion usa la collation de la columna, la misma que aplicara el indice unico. El DISTINCT fuerza a materializar la tabla derivada (evita el error 1093 de MySQL/MariaDB).
UPDATE cat_valor_prenda v
JOIN (
    SELECT DISTINCT d.id_valor_atributo AS id
    FROM cat_valor_prenda d
    JOIN cat_valor_prenda o
      ON o.clave = d.clave AND o.id_valor_atributo < d.id_valor_atributo
) dup ON dup.id = v.id_valor_atributo
SET v.clave = CONCAT(LEFT(v.clave, 20 - CHAR_LENGTH(CONCAT('-', v.id_valor_atributo))), '-', v.id_valor_atributo),
    v.pendiente_revision = 1;
--rollback SELECT 'sin rollback de datos: las claves renombradas <clave>-<id> se conservan';

--changeset emm-a:041-6
--comment: D-09 - Descripcion general faltante (registros dados de alta despues del changeset 024): se usa la clave como valor provisional y se marca para revision.
UPDATE cat_valor_prenda
SET descripcion = clave, pendiente_revision = 1
WHERE descripcion IS NULL OR TRIM(descripcion) = '';
--rollback SELECT 'sin rollback de datos: las descripciones provisionales se conservan';

--changeset emm-a:041-7
--comment: D-09 - Clave unica global (no por sucursal) y obligatoria; descripcion general obligatoria otra vez (revierte 024).
ALTER TABLE cat_valor_prenda
  MODIFY COLUMN clave VARCHAR(20) NOT NULL,
  MODIFY COLUMN descripcion VARCHAR(100) NOT NULL,
  ADD CONSTRAINT uk_cat_valor_prenda_clave UNIQUE (clave);
--rollback ALTER TABLE cat_valor_prenda DROP INDEX uk_cat_valor_prenda_clave, MODIFY COLUMN clave VARCHAR(20) NULL, MODIFY COLUMN descripcion VARCHAR(100) NULL;

--changeset emm-a:041-8
--comment: D-09 / Q6 - Roles que pueden dar de alta, editar y borrar valores del catalogo de prendas. CSV de ids de rol, igual que ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO. Por defecto solo Sistemas (1); las sucursales solo consultan.
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
VALUES ('ROLES_PERMITIDOS_CATALOGO_PRENDAS', '1', NULL);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CATALOGO_PRENDAS';
