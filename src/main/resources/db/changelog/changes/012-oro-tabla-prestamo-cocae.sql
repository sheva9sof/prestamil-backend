--liquibase formatted sql

--changeset emm-a:012-1
--comment: Tabla global de %Prestamo COCAE (8 kilates x 3 hechuras) por sucursal.
--          Fuente de verdad para derivar precio_base en plazo_hechura_alhaja;
--          reemplaza el uso de factor_fundir/factor_normal/factor_especial de precio_oro
--          para ese proposito (esos factores se conservan como valores de referencia
--          en la pantalla "Precio del Oro", ver D-02, pero dejan de aplicarse en el calculo).
CREATE TABLE oro_tabla_prestamo (
  sucursal_id     INT NOT NULL,
  kilataje        INT NOT NULL,
  hechura         VARCHAR(1) NOT NULL,
  porc_prestamo   DECIMAL(7,4) NOT NULL,
  actualizado_en  DATETIME NOT NULL,
  PRIMARY KEY (sucursal_id, kilataje, hechura),
  CONSTRAINT fk_oro_tabla_prestamo_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id)
);

--changeset emm-a:012-2
--comment: Import de valores reales COCAE v3.80 (capturas DIARIO tabla 7 / SEMANAL tabla 8,
--          precio base 21K = 1679.50) - sucursal 1 Tierra Colorada. 24K = 0% (D-04, no prendable).
INSERT INTO oro_tabla_prestamo (sucursal_id, kilataje, hechura, porc_prestamo, actualizado_en) VALUES
  (1, 6,  'F', 24.7600, NOW()), (1, 6,  'N', 26.7900, NOW()), (1, 6,  'E', 30.7300, NOW()),
  (1, 8,  'F', 60.1100, NOW()), (1, 8,  'N', 62.0400, NOW()), (1, 8,  'E', 64.1000, NOW()),
  (1, 10, 'F', 61.0500, NOW()), (1, 10, 'N', 62.6300, NOW()), (1, 10, 'E', 64.1500, NOW()),
  (1, 12, 'F', 61.6300, NOW()), (1, 12, 'N', 62.9500, NOW()), (1, 12, 'E', 64.2400, NOW()),
  (1, 14, 'F', 62.1400, NOW()), (1, 14, 'N', 63.2700, NOW()), (1, 14, 'E', 64.3900, NOW()),
  (1, 18, 'F', 62.5200, NOW()), (1, 18, 'N', 63.4000, NOW()), (1, 18, 'E', 66.3400, NOW()),
  (1, 21, 'F', 62.6700, NOW()), (1, 21, 'N', 63.4400, NOW()), (1, 21, 'E', 66.0800, NOW()),
  (1, 24, 'F', 0.0000,  NOW()), (1, 24, 'N', 0.0000,  NOW()), (1, 24, 'E', 0.0000,  NOW());
