--liquibase formatted sql

-- ============================================================================
-- CAJ-01d (PLAN-CORTE-CAJA, Q-C3, Jorge 2026-10-30): los abonos parciales no
-- existen como tal. RP es un refrendo y RPX un refrendo extemporaneo, asi que el
-- concepto REFRENDO_PARCIAL desaparece del enum ConceptoCaja:
--   * RP  -> REFRENDO              (renglon REFRENDOS)
--   * RPX -> REFRENDO_EXTEMPORANEO (renglon REFRENDOS EXTEMPORANEOS)
--
-- Todas las filas REFRENDO_PARCIAL vienen de un RP o RPX (072 y MovimientoCajaService
-- siempre ligan el movimiento de contrato). El LEFT JOIN asegura que ninguna se
-- quede con el valor viejo, que Hibernate ya no puede leer: una fila sin origen
-- pasaria a REFRENDO.
--
-- Las cancelaciones (DEVOLUCION_COBRO) no cambian: su renglon se resuelve con el
-- movimiento de contrato original (ResolutorRenglonCorte). Los RC tampoco: se
-- dividen al calcular el corte, no en la tabla.
-- ============================================================================

--changeset emm-a:082-1
--comment: CAJ-01d - Q-C3: REFRENDO_PARCIAL pasa a REFRENDO (RP) o REFRENDO_EXTEMPORANEO (RPX).
UPDATE movimiento_caja mc
LEFT JOIN movimiento_contrato m ON m.id = mc.origen_id AND mc.origen_tipo = 'MOVIMIENTO_CONTRATO'
SET mc.concepto_caja = CASE WHEN m.tipo = 'RPX' THEN 'REFRENDO_EXTEMPORANEO' ELSE 'REFRENDO' END
WHERE mc.concepto_caja = 'REFRENDO_PARCIAL';
--rollback UPDATE movimiento_caja mc JOIN movimiento_contrato m ON m.id = mc.origen_id AND mc.origen_tipo = 'MOVIMIENTO_CONTRATO' SET mc.concepto_caja = 'REFRENDO_PARCIAL' WHERE m.tipo IN ('RP', 'RPX') AND mc.concepto_caja IN ('REFRENDO', 'REFRENDO_EXTEMPORANEO');
