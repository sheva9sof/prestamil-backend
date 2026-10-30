--liquibase formatted sql

-- ============================================================================
-- CAJ-01b (PLAN-CORTE-CAJA): backfill de las columnas de 071 para las filas que ya
-- existen en movimiento_caja.
--
-- Hasta este changeset las 5 rutas que escribian en movimiento_caja pasaban siempre el
-- movimiento de contrato que origino el flujo, asi que todas las filas se resuelven con
-- un JOIN a movimiento_contrato:
--   * EMP + SALIDA  -> EMPENO              (prestamo entregado al crear el contrato)
--   * EMP + ENTRADA -> CANCELACION_EMPENO  (el cliente devuelve el prestamo)
--   * otro + SALIDA -> DEVOLUCION_COBRO    (cancelacion de un cobro)
--   * otro + ENTRADA-> concepto del cobro segun movimiento_contrato.tipo
--
-- Importes:
--   * EMP (y su cancelacion): todo en efectivo, no pasa por la ventana de Cobro.
--   * Cobros y sus cancelaciones: el mismo reparto que el cobro original,
--     efectivo neto = importe_efectivo - cambio_entregado; tarjeta = importe_tarjeta.
--   * Cobros anteriores a la ventana de Cobro (sin importe_efectivo ni importe_tarjeta):
--     todo en efectivo, que es como se registraron.
-- `monto` no se toca.
--
-- 072-2 vuelve NOT NULL concepto_caja e importes. Una precondicion detiene la migracion
-- si quedara alguna fila sin resolver (con sql_mode no estricto el ALTER convertiria los
-- NULL a '' / 0 en silencio), en vez de dejar filas fuera del corte.
-- ============================================================================

--changeset emm-a:072-1
--comment: CAJ-01b - Backfill de concepto_caja, importes separados y origen desde movimiento_contrato.
UPDATE movimiento_caja mc
JOIN movimiento_contrato m ON m.id = mc.id_movimiento_contrato
SET mc.concepto_caja = CASE
        WHEN m.tipo = 'EMP' AND mc.tipo = 'SALIDA'  THEN 'EMPENO'
        WHEN m.tipo = 'EMP' AND mc.tipo = 'ENTRADA' THEN 'CANCELACION_EMPENO'
        WHEN mc.tipo = 'SALIDA'                     THEN 'DEVOLUCION_COBRO'
        WHEN m.tipo IN ('RF', 'RPG')                THEN 'REFRENDO'
        WHEN m.tipo = 'RX'                          THEN 'REFRENDO_EXTEMPORANEO'
        WHEN m.tipo IN ('RP', 'RPX')                THEN 'REFRENDO_PARCIAL'
        WHEN m.tipo = 'RC'                          THEN 'ABONO_CAPITAL'
        WHEN m.tipo = 'FI'                          THEN 'FINIQUITO'
        WHEN m.tipo = 'FX'                          THEN 'FINIQUITO_EXTEMPORANEO'
        WHEN m.tipo = 'RE'                          THEN 'REPOSICION'
    END,
    mc.importe_efectivo = CASE
        WHEN m.tipo = 'EMP' OR (m.importe_efectivo IS NULL AND m.importe_tarjeta IS NULL) THEN mc.monto
        ELSE COALESCE(m.importe_efectivo, 0) - COALESCE(m.cambio_entregado, 0)
    END,
    mc.importe_tarjeta = CASE
        WHEN m.tipo = 'EMP' THEN 0
        ELSE COALESCE(m.importe_tarjeta, 0)
    END,
    mc.origen_tipo = 'MOVIMIENTO_CONTRATO',
    mc.origen_id   = mc.id_movimiento_contrato
WHERE mc.concepto_caja IS NULL;
--rollback UPDATE movimiento_caja SET concepto_caja = NULL, importe_efectivo = NULL, importe_tarjeta = NULL, origen_tipo = NULL, origen_id = NULL;

--changeset emm-a:072-2
--comment: CAJ-01b - Todo flujo de caja lleva concepto e importes separados. Falla si el backfill dejo filas sin resolver.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM movimiento_caja WHERE concepto_caja IS NULL OR importe_efectivo IS NULL OR importe_tarjeta IS NULL
ALTER TABLE movimiento_caja
  MODIFY concepto_caja    VARCHAR(40)   NOT NULL COMMENT 'Enum ConceptoCaja: renglon del corte de caja',
  MODIFY importe_efectivo DECIMAL(18,2) NOT NULL COMMENT 'Efectivo que entra o sale de la gaveta, neto del cambio',
  MODIFY importe_tarjeta  DECIMAL(18,2) NOT NULL COMMENT 'Parte pagada o devuelta con tarjeta; informativa, no entra al arqueo';
--rollback ALTER TABLE movimiento_caja MODIFY concepto_caja VARCHAR(40) NULL COMMENT 'Enum ConceptoCaja: renglon del corte de caja', MODIFY importe_efectivo DECIMAL(18,2) NULL COMMENT 'Efectivo que entra o sale de la gaveta, neto del cambio', MODIFY importe_tarjeta DECIMAL(18,2) NULL COMMENT 'Parte pagada o devuelta con tarjeta; informativa, no entra al arqueo';
