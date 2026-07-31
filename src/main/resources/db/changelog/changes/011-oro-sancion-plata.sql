--liquibase formatted sql

--changeset emm-a:011-1
--comment: Tabla precio_oro — precio base del gramo de oro 24K vigente por sucursal
CREATE TABLE precio_oro (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  sucursal_id        INT NOT NULL,
  precio_gramo_24k   DECIMAL(12,4) NOT NULL DEFAULT 0.0000,
  calcular_sobre     VARCHAR(20) NOT NULL DEFAULT 'PRESTAMO',
  base_kilataje      INT NOT NULL DEFAULT 24,
  actualizado_en     DATETIME NOT NULL,
  actualizado_por    VARCHAR(80),
  CONSTRAINT uq_precio_oro_sucursal UNIQUE (sucursal_id),
  CONSTRAINT fk_precio_oro_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id)
);

--changeset emm-a:011-2
--comment: Fila inicial de precio_oro para la sucursal 1
INSERT INTO precio_oro (sucursal_id, precio_gramo_24k, calcular_sobre, base_kilataje, actualizado_en)
VALUES (1, 0.0000, 'PRESTAMO', 24, NOW());

--changeset emm-a:011-3
--comment: Sanción por extemporaneidad (2% semanal por defecto) en plazo_parametro
ALTER TABLE plazo_parametro
  ADD COLUMN porc_sancion_semanal DECIMAL(9,4) NOT NULL DEFAULT 2.0000 AFTER aplicar_sancion_por_periodo;

--changeset emm-a:011-4
--comment: Parámetros de plata (leyes 925/725) y precio del gramo de plata en plazo_parametro
ALTER TABLE plazo_parametro
  ADD COLUMN ley_925 DECIMAL(9,4) NOT NULL DEFAULT 0.0000 AFTER porc_sancion_semanal,
  ADD COLUMN ley_725 DECIMAL(9,4) NOT NULL DEFAULT 0.0000 AFTER ley_925,
  ADD COLUMN precio_gramo_plata DECIMAL(12,4) NOT NULL DEFAULT 0.0000 AFTER ley_725;

--changeset emm-a:011-5
--comment: Ley de la prenda de plata en la partida del contrato
ALTER TABLE partida_contrato
  ADD COLUMN ley DECIMAL(9,4) AFTER kilataje;

--changeset emm-a:011-6
--comment: Plazo Diario (1 día) y su vínculo con ALHAJA para que sea seleccionable
INSERT IGNORE INTO plazo (id, nombre, dias_por_periodo, numero_periodos, activo, creado_en, actualizado_en)
VALUES (4, 'Diario', 1, 30, 1, NOW(), NOW());

INSERT IGNORE INTO plazo_prenda (plazo_id, tipo_prenda_id) VALUES (4, 1);

INSERT IGNORE INTO plazo_parametro
  (plazo_id, tipo_prenda_id, sucursal_id,
   porc_interes, porc_almacen, porc_gastos_admin, porc_interes_total,
   cat, num_max_refrendos, porc_prestamo_s_avaluo, usa_avaluo_real,
   porc_incremento_avaluo, porc_prestamo_s_avaluo_real, cobrar_reposicion_contrato,
   reposicion_es_porcentaje, porc_reposicion, monto_reposicion,
   comision_por_venta_prenda, aplicar_sancion_por_periodo, porc_sancion_semanal,
   ley_925, ley_725, precio_gramo_plata,
   dias_gracia_sin_interes, dias_antes_pase_venta, importe_min_prestamo,
   creado_en, actualizado_en)
VALUES
  (4, 1, 1, 1.1240, 0.6000, 0.0000, 1.7240, 0.0000, 0, 0.0000, 1,
   50.0000, 0.0000, 1, 0, 0.0000, 0.00, 18.0000, 1, 2.0000,
   0.0000, 0.0000, 0.0000, 2, 14, 50.00, NOW(), NOW());

--changeset emm-a:011-7
--comment: Columnas de sanción y abono a capital en movimiento_contrato
ALTER TABLE movimiento_contrato
  ADD COLUMN sancion DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER interes,
  ADD COLUMN abono_capital DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER sancion,
  ADD COLUMN semanas_vencidas INT NOT NULL DEFAULT 0 AFTER abono_capital;

--changeset emm-a:011-8
--comment: Factores de hechura (calidad del oro) editables por sucursal — Fundir 90%, Normal 100%, Especial 110%
ALTER TABLE precio_oro
  ADD COLUMN factor_fundir   DECIMAL(7,4) NOT NULL DEFAULT 90.0000  AFTER base_kilataje,
  ADD COLUMN factor_normal   DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_fundir,
  ADD COLUMN factor_especial DECIMAL(7,4) NOT NULL DEFAULT 110.0000 AFTER factor_normal;

--changeset emm-a:011-9
--comment: Ensanchar porc_aumento — se almacena como porcentaje entero (p.ej. 11.0000), DECIMAL(5,4) no alcanza
ALTER TABLE plazo_hechura_alhaja
  MODIFY COLUMN porc_aumento DECIMAL(7,4) NOT NULL;
