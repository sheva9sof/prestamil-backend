--liquibase formatted sql

--changeset emm-a:030-1
--comment: Descuento parametrizado sobre intereses (RN-27, COCAE "Desc. s/interes"). Solo lo edita Sistemas; el cajero no lo captura. Se aplica al interes de cualquier movimiento (refrendo, parcial, finiquito) antes del IVA. 0 = sin descuento; se toma el valor vigente al momento de la operacion (no snapshot del contrato).
ALTER TABLE plazo_parametro
  ADD COLUMN porc_descuento_interes DECIMAL(9,4) NOT NULL DEFAULT 0.0000
  AFTER porc_sancion_semanal;
--rollback ALTER TABLE plazo_parametro DROP COLUMN porc_descuento_interes;
