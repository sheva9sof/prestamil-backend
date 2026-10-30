--liquibase formatted sql

--changeset emm-a:076-1
--comment: Revision del pase de almoneda (2026-10-30) - El detalle guarda el vencimiento y el saldo capital del contrato el dia del pase. "Cartera vencida" mostraba los valores actuales del contrato, que cambian si despues se refrenda o abona; consultar un pase pasado debe mostrar lo que habia ese dia.
ALTER TABLE pase_almoneda_detalle
  ADD COLUMN fecha_vencimiento DATE          NULL COMMENT 'Vencimiento del contrato el dia del pase',
  ADD COLUMN saldo_capital     DECIMAL(18,2) NULL COMMENT 'Saldo capital del contrato el dia del pase';
--rollback ALTER TABLE pase_almoneda_detalle DROP COLUMN fecha_vencimiento, DROP COLUMN saldo_capital;
