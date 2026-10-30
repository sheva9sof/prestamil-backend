--liquibase formatted sql

--changeset emm-a:077-1
--comment: Backfill de 076 - Las filas anteriores no guardaron vencimiento ni saldo del dia del pase; se llenan con los datos actuales del contrato (es lo que la pantalla mostraba hasta hoy, asi que no cambia lo que se ve).
UPDATE pase_almoneda_detalle d
  JOIN contrato c ON c.id = d.id_contrato
   SET d.fecha_vencimiento = c.fecha_vencimiento,
       d.saldo_capital     = c.saldo_capital
 WHERE d.fecha_vencimiento IS NULL;
--rollback UPDATE pase_almoneda_detalle SET fecha_vencimiento = NULL, saldo_capital = NULL;
