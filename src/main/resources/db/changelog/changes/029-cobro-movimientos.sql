--liquibase formatted sql

--changeset emm-a:029-1
--comment: Contador del folio de nota por sucursal (RN-25, COCAE "Folio No: 27323"). Distinto del numero de contrato. Se incrementa con bloqueo de fila en la misma transaccion del movimiento, asi un cobro que falla no deja huecos. Para continuar la numeracion de COCAE basta con ajustar ultimo_folio.
CREATE TABLE folio_nota (
  id_sucursal  INT NOT NULL PRIMARY KEY,
  ultimo_folio INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_folio_nota_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id)
);
INSERT INTO folio_nota (id_sucursal, ultimo_folio) SELECT id, 0 FROM sucursal;
--rollback DROP TABLE folio_nota;

--changeset emm-a:029-2
--comment: Folio de la nota (ticket) de cada movimiento cobrado. NULL en EMP y en movimientos anteriores a F3.
ALTER TABLE movimiento_contrato
  ADD COLUMN folio_nota INT NULL COMMENT 'Folio consecutivo de la nota por sucursal (RN-25)' AFTER request_id;
--rollback ALTER TABLE movimiento_contrato DROP COLUMN folio_nota;
