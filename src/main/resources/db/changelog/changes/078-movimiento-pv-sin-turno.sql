--liquibase formatted sql

--changeset emm-a:078-1
--comment: Revision del pase de almoneda (2026-10-30) - El pase ya no depende de abrir turno: corre al arrancar, a las 00:05, al abrir turno y al consultar resultados, y se pone al dia un dia a la vez. El movimiento PV que registra es automatico y puede no tener turno ni usuario (por ejemplo, el pase de las 00:05). Los demas movimientos siguen exigiendo turno y usuario en el servicio.
ALTER TABLE movimiento_contrato
  MODIFY COLUMN id_turno   INT NULL,
  MODIFY COLUMN id_usuario INT NULL;
--rollback ALTER TABLE movimiento_contrato MODIFY COLUMN id_turno INT NOT NULL, MODIFY COLUMN id_usuario INT NOT NULL;
