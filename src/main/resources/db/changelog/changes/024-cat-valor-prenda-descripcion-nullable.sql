--liquibase formatted sql

--changeset emm-a:024-1
--comment: cat_valor_prenda.descripcion pasa a NULL: el modal del catalogo ya no la captura, el item se identifica por clave alfanumerica. Los nombres historicos existentes se conservan intactos.
ALTER TABLE cat_valor_prenda
  MODIFY COLUMN descripcion VARCHAR(100) NULL;
--rollback ALTER TABLE cat_valor_prenda MODIFY COLUMN descripcion VARCHAR(100) NOT NULL;
