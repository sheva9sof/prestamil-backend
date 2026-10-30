--liquibase formatted sql

-- ============================================================================
-- CAJ-01b (PLAN-CORTE-CAJA, hallazgos H-01 y H-02): movimiento_caja estructurado.
--
-- H-01: hasta ahora un cobro con tarjeta entraba completo como ENTRADA de caja y la
-- tarjeta solo quedaba en el texto de `concepto`. El arqueo solo debe sumar efectivo
-- (RC-06), asi que el importe se separa en importe_efectivo (neto del cambio) e
-- importe_tarjeta. `monto` se conserva y sigue siendo la suma de los dos.
--
-- H-02: `concepto` es texto libre. concepto_caja guarda el enum ConceptoCaja, que
-- dice a que renglon del corte va cada flujo.
--
-- origen_tipo / origen_id identifican el registro que origino el flujo SIN FK (A-02):
-- las ventas de Salvador viven en otra tabla y el orden de los changesets no importa.
--
-- Las columnas nacen NULL; 072 las llena para las filas existentes y despues las
-- vuelve NOT NULL.
-- ============================================================================

--changeset emm-a:071-1
--comment: CAJ-01b - Concepto estructurado, importes separados por forma de pago y origen del flujo de caja.
ALTER TABLE movimiento_caja
  ADD COLUMN concepto_caja    VARCHAR(40)   NULL COMMENT 'Enum ConceptoCaja: renglon del corte de caja' AFTER concepto,
  ADD COLUMN importe_efectivo DECIMAL(18,2) NULL COMMENT 'Efectivo que entra o sale de la gaveta, neto del cambio' AFTER monto,
  ADD COLUMN importe_tarjeta  DECIMAL(18,2) NULL COMMENT 'Parte pagada o devuelta con tarjeta; informativa, no entra al arqueo' AFTER importe_efectivo,
  ADD COLUMN origen_tipo      VARCHAR(30)   NULL COMMENT 'Enum OrigenCaja: MOVIMIENTO_CONTRATO | VENTA | CANCELACION_VENTA' AFTER id_movimiento_contrato,
  ADD COLUMN origen_id        BIGINT        NULL COMMENT 'Id del registro de origen, sin FK (A-02)' AFTER origen_tipo;
--rollback ALTER TABLE movimiento_caja DROP COLUMN origen_id, DROP COLUMN origen_tipo, DROP COLUMN importe_tarjeta, DROP COLUMN importe_efectivo, DROP COLUMN concepto_caja;

--changeset emm-a:071-2
--comment: CAJ-01b - Indice del corte de caja: movimientos de una sucursal en un rango de fechas, agrupados por concepto.
CREATE INDEX idx_mov_caja_sucursal_fecha_concepto ON movimiento_caja (id_sucursal, fecha, concepto_caja);
--rollback DROP INDEX idx_mov_caja_sucursal_fecha_concepto ON movimiento_caja;
