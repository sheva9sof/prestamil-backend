--liquibase formatted sql

-- ============================================================================
-- D-09 (ajuste 2026-10-08, segun la conversacion con Jorge): varias prendas
-- pueden compartir la misma clave; lo que las distingue es la descripcion
-- general y el kilataje (ej. ESC "ESCLAVA 10K" y ESC "ESCLAVA 14K"). Se quita
-- el indice unico que puso el changeset 041 y se deshace el renombrado de
-- claves duplicadas que hizo 041-5. La clave sigue siendo obligatoria.
--
-- De paso se corrige el nombre contiene_piedad -> contiene_piedra (es "con
-- piedra?" de COCAE).
-- ============================================================================

--changeset emm-a:042-1
--comment: D-09 - La clave del catalogo deja de ser unica: varias prendas comparten clave y se distinguen por descripcion general y kilataje.
ALTER TABLE cat_valor_prenda DROP INDEX uk_cat_valor_prenda_clave;
--rollback ALTER TABLE cat_valor_prenda ADD CONSTRAINT uk_cat_valor_prenda_clave UNIQUE (clave);

--changeset emm-a:042-2
--comment: D-09 - Revierte el renombrado <clave>-<id> de 041-5 en los registros que Sistemas todavia no ha corregido (pendiente_revision = 1). Si la descripcion era la provisional (igual a la clave renombrada) tambien se restaura y el registro sigue pendiente; si la descripcion era real, el registro deja de estar pendiente. Las claves que 041-5 trunco a 20 caracteres no se pueden recuperar completas.
UPDATE cat_valor_prenda
SET pendiente_revision = CASE WHEN descripcion = clave THEN 1 ELSE 0 END,
    descripcion = CASE
        WHEN descripcion = clave
        THEN LEFT(clave, CHAR_LENGTH(clave) - CHAR_LENGTH(CONCAT('-', id_valor_atributo)))
        ELSE descripcion END,
    clave = LEFT(clave, CHAR_LENGTH(clave) - CHAR_LENGTH(CONCAT('-', id_valor_atributo)))
WHERE pendiente_revision = 1
  AND clave LIKE CONCAT('%-', id_valor_atributo)
  AND clave NOT LIKE 'SIN-CLAVE-%';
--rollback SELECT 'sin rollback de datos: las claves restauradas se conservan';

--changeset emm-a:042-3
--comment: Corrige el nombre de la columna: contiene_piedad -> contiene_piedra (indica si la prenda lleva piedra). CHANGE COLUMN en lugar de RENAME COLUMN por compatibilidad con MariaDB 10.4.
ALTER TABLE cat_valor_prenda CHANGE COLUMN contiene_piedad contiene_piedra SMALLINT NULL;
--rollback ALTER TABLE cat_valor_prenda CHANGE COLUMN contiene_piedra contiene_piedad SMALLINT NULL;
