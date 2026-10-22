--liquibase formatted sql

-- ============================================================================
-- D-10 (PLAN-CORRECCIONES-07OCT): bolsa y codigo de barras por partida.
--
-- En COCAE todas las prendas de un contrato se guardan en una bolsa con su
-- numero, independiente del numero de contrato (se van separando con el tiempo
-- por las cancelaciones). Cada partida lleva un codigo de barras unico, como un
-- SKU: consecutivo del sistema, nunca repetido.
--
-- Los dos consecutivos viven en tablas de contadores con el mismo patron que
-- folio_nota (changeset 029): se incrementan con bloqueo de fila en la misma
-- transaccion que crea el contrato, asi un contrato que falla no deja huecos y
-- dos avaluos simultaneos no toman el mismo numero. Como el contador nunca baja,
-- un codigo de barras o una bolsa no se reutilizan aunque se borre el registro.
-- ============================================================================

--changeset emm-a:043-1
--comment: D-10 - Contador del numero de bolsa por sucursal. Para continuar la numeracion de COCAE basta con ajustar ultimo_folio.
CREATE TABLE folio_bolsa (
  id_sucursal  INT NOT NULL PRIMARY KEY,
  ultimo_folio INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_folio_bolsa_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id)
);
INSERT INTO folio_bolsa (id_sucursal, ultimo_folio) SELECT id, 0 FROM sucursal;
--rollback DROP TABLE folio_bolsa;

--changeset emm-a:043-2
--comment: D-10 - Numero de bolsa del contrato, consecutivo por sucursal. Los contratos existentes reciben bolsa en orden de creacion y el contador continua desde el ultimo asignado.
ALTER TABLE contrato
  ADD COLUMN num_bolsa INT NULL COMMENT 'Numero de bolsa, consecutivo por sucursal (D-10)' AFTER folio;
UPDATE contrato c
  JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY id_sucursal ORDER BY id) AS rn FROM contrato) x
    ON x.id = c.id
SET c.num_bolsa = x.rn;
UPDATE folio_bolsa f
SET f.ultimo_folio = COALESCE((SELECT MAX(c.num_bolsa) FROM contrato c WHERE c.id_sucursal = f.id_sucursal), 0);
ALTER TABLE contrato MODIFY COLUMN num_bolsa INT NOT NULL COMMENT 'Numero de bolsa, consecutivo por sucursal (D-10)';
ALTER TABLE contrato ADD CONSTRAINT uk_contrato_sucursal_bolsa UNIQUE (id_sucursal, num_bolsa);
--rollback ALTER TABLE contrato DROP INDEX uk_contrato_sucursal_bolsa;
--rollback ALTER TABLE contrato DROP COLUMN num_bolsa;

--changeset emm-a:043-3
--comment: D-10 - Contador global (una sola fila, id = 1) del codigo de barras de las partidas. Es global y no por sucursal para que el codigo no se repita entre sucursales.
CREATE TABLE folio_codigo_barras (
  id           INT    NOT NULL PRIMARY KEY,
  ultimo_folio BIGINT NOT NULL DEFAULT 0
);
INSERT INTO folio_codigo_barras (id, ultimo_folio) VALUES (1, 0);
--rollback DROP TABLE folio_codigo_barras;

--changeset emm-a:043-4
--comment: D-10 - Codigo de barras de cada partida, unico global. Las partidas existentes reciben codigo en orden de creacion y el contador continua desde el ultimo asignado.
ALTER TABLE partida_contrato
  ADD COLUMN codigo_barras VARCHAR(20) NULL COMMENT 'Codigo de barras unico global de la partida (D-10)' AFTER num_partida;
UPDATE partida_contrato p
  JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM partida_contrato) x
    ON x.id = p.id
SET p.codigo_barras = LPAD(x.rn, 10, '0');
UPDATE folio_codigo_barras
SET ultimo_folio = (SELECT COUNT(*) FROM partida_contrato)
WHERE id = 1;
ALTER TABLE partida_contrato MODIFY COLUMN codigo_barras VARCHAR(20) NOT NULL COMMENT 'Codigo de barras unico global de la partida (D-10)';
ALTER TABLE partida_contrato ADD CONSTRAINT uk_partida_contrato_codigo_barras UNIQUE (codigo_barras);
--rollback ALTER TABLE partida_contrato DROP INDEX uk_partida_contrato_codigo_barras;
--rollback ALTER TABLE partida_contrato DROP COLUMN codigo_barras;
