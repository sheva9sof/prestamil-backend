--liquibase formatted sql

--changeset emm-a:023-1
--comment: cat_valor_prenda.clave pasa de INT a VARCHAR(20) alfanumerico (confirmado con Jorge, 2026-09-08). Sin perdida de datos: MariaDB convierte automaticamente los enteros existentes a texto.
ALTER TABLE cat_valor_prenda
  MODIFY COLUMN clave VARCHAR(20) NULL;
--rollback ALTER TABLE cat_valor_prenda MODIFY COLUMN clave INT(11) NULL;
