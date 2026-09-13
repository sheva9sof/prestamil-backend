--liquibase formatted sql

--changeset emm-a:026-1
--comment: Snapshot de los 7 parametros de calculo vigentes al crear el contrato. La reimpresion de un contrato ya firmado debe usar SIEMPRE su snapshot (requisito PROFECO: montos inmutables). Los contratos historicos quedan con las 7 columnas en NULL y recalculan con la config vigente como fallback documentado. Congelar los 7 y no solo sancion/IVA evita la divergencia interna en el PDF entre filas normales S1..SN (que dependen de interes/almacen/gastosAdmin) y las extemporaneas S5/S6 (que dependen de sancion/IVA).
ALTER TABLE contrato
  ADD COLUMN snap_porc_sancion_semanal    DECIMAL(9,4)  NULL COMMENT 'porc_sancion_semanal vigente al crearse',
  ADD COLUMN snap_dias_gracia_sancion     INT           NULL COMMENT 'gracia de sancion vigente (equivale a dias_gracia_sin_interes)',
  ADD COLUMN snap_aplicar_sancion_periodo TINYINT(1)    NULL COMMENT 'aplicar_sancion_por_periodo vigente al crearse',
  ADD COLUMN snap_iva_porcentaje          DECIMAL(10,2) NULL COMMENT 'IVA de parametros_sistema(id=8) al crearse',
  ADD COLUMN snap_porc_interes            DECIMAL(9,4)  NULL COMMENT 'porc_interes vigente al crearse',
  ADD COLUMN snap_porc_almacen            DECIMAL(9,4)  NULL COMMENT 'porc_almacen vigente al crearse',
  ADD COLUMN snap_porc_gastos_admin       DECIMAL(9,4)  NULL COMMENT 'porc_gastos_admin vigente al crearse';
--rollback ALTER TABLE contrato DROP COLUMN snap_porc_sancion_semanal, DROP COLUMN snap_dias_gracia_sancion, DROP COLUMN snap_aplicar_sancion_periodo, DROP COLUMN snap_iva_porcentaje, DROP COLUMN snap_porc_interes, DROP COLUMN snap_porc_almacen, DROP COLUMN snap_porc_gastos_admin;
