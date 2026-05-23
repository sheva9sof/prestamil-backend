--liquibase formatted sql

--changeset emm-a:010-1
--comment: Agregar columna porc_incremento_avaluo a plazo_parametro
ALTER TABLE `plazo_parametro`
  ADD COLUMN `porc_incremento_avaluo` DECIMAL(9,4) NOT NULL DEFAULT 50.0000
  AFTER `usa_avaluo_real`;

--changeset emm-a:010-2
--comment: Insertar parámetros faltantes para Quincenal/Plata y Mensual/Varios
-- Garantizar que plazo_prenda tiene los pares requeridos (idempotente)
INSERT IGNORE INTO `plazo_prenda` (`plazo_id`, `tipo_prenda_id`) VALUES
  (2, 4),
  (3, 3);

INSERT IGNORE INTO `plazo_parametro`
  (`plazo_id`, `tipo_prenda_id`, `sucursal_id`,
   `porc_interes`, `porc_almacen`, `porc_gastos_admin`, `porc_interes_total`,
   `cat`, `num_max_refrendos`, `porc_prestamo_s_avaluo`, `usa_avaluo_real`,
   `porc_incremento_avaluo`,
   `porc_prestamo_s_avaluo_real`, `cobrar_reposicion_contrato`,
   `reposicion_es_porcentaje`, `porc_reposicion`, `monto_reposicion`,
   `comision_por_venta_prenda`, `aplicar_sancion_por_periodo`,
   `dias_gracia_sin_interes`, `dias_antes_pase_venta`, `importe_min_prestamo`,
   `creado_en`, `actualizado_en`)
VALUES
  (2, 4, 1,  1.1240, 0.6000, 0.0000, 1.7240, 0.0000, 0, 0.0000, 1,
   50.0000, 0.0000, 1, 0, 0.0000, 0.00, 18.0000, 1, 2, 14, 50.00,
   NOW(), NOW()),
  (3, 3, 1,  1.1240, 0.6000, 0.0000, 1.7240, 0.0000, 0, 0.0000, 1,
   50.0000, 0.0000, 1, 0, 0.0000, 0.00, 18.0000, 1, 2, 14, 50.00,
   NOW(), NOW());
