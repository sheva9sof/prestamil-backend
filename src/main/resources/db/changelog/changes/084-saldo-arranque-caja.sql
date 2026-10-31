--liquibase formatted sql

-- ============================================================================
-- CAJ-05 (PLAN-CORTE-CAJA): saldo de arranque del corte de caja, temporal hasta
-- CAJ-06 (pantalla de Sistemas por sucursal, B-04). Lo lee
-- SaldoArranqueConfiguracionProvider:
--   CAJA_FECHA_ARRANQUE  primer dia con corte (yyyy-MM-dd); antes no hay cifras
--   CAJA_SALDO_ARRANQUE  saldo de la boveda al abrir ese dia
-- Default: el dia del primer traspaso registrado (o hoy, si no hay) con saldo
-- 0.00, asi los traspasos que ya existen quedan dentro del corte. Sistemas debe
-- corregir los dos valores con el saldo real de la migracion.
-- ============================================================================

--changeset emm-a:084-1
--comment: CAJ-05 - Saldo de arranque de caja (temporal hasta CAJ-06).
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
SELECT 'CAJA_FECHA_ARRANQUE',
       DATE_FORMAT(COALESCE((SELECT MIN(`fecha_operacion`) FROM `traspaso_caja`), CURDATE()), '%Y-%m-%d'),
       NULL
WHERE NOT EXISTS (SELECT 1 FROM `configuraciones` WHERE `configuracion` = 'CAJA_FECHA_ARRANQUE');
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
SELECT 'CAJA_SALDO_ARRANQUE', '0.00', NULL
WHERE NOT EXISTS (SELECT 1 FROM `configuraciones` WHERE `configuracion` = 'CAJA_SALDO_ARRANQUE');
--rollback DELETE FROM `configuraciones` WHERE `configuracion` IN ('CAJA_FECHA_ARRANQUE', 'CAJA_SALDO_ARRANQUE');
