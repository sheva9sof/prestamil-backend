--liquibase formatted sql

-- ============================================================================
-- CAJ-01c (PLAN-CORTE-CAJA): semilla de cajas y denominaciones.
--
-- Cajas: las tres de COCAE en cada sucursal existente. Una sola VENTANILLA por
-- sucursal porque hoy hay un solo turno activo en el sistema (H-05); cuando haya
-- turnos simultaneos (B-05) se agregan 004, 005, ...
--
-- Denominaciones: las 14 de COCAE, de 1000 a 0.01 (Q-10). Si Jorge decide quitar
-- 0.01 o agregar 0.05, se cambia con un changeset nuevo (activa = 0 o INSERT).
-- ============================================================================

--changeset emm-a:074-1
--comment: CAJ-01c - Cajas 001 CUENTA CONCENTRADORA, 002 BOVEDA y 003 VENTANILLA por sucursal (RC-01, H-05).
INSERT INTO caja (id_sucursal, tipo, clave, nombre, activa)
SELECT s.id, 'CONCENTRADORA', '001', 'CUENTA CONCENTRADORA', 1 FROM sucursal s;
INSERT INTO caja (id_sucursal, tipo, clave, nombre, activa)
SELECT s.id, 'BOVEDA', '002', 'BÓVEDA', 1 FROM sucursal s;
INSERT INTO caja (id_sucursal, tipo, clave, nombre, activa)
SELECT s.id, 'VENTANILLA', '003', 'VENTANILLA', 1 FROM sucursal s;
--rollback DELETE FROM caja WHERE clave IN ('001', '002', '003');

--changeset emm-a:074-2
--comment: CAJ-01c - Las 14 denominaciones de COCAE (RC-03, Q-10).
INSERT INTO denominacion (valor, tipo, orden, activa) VALUES
  (1000.00, 'BILLETE',  1, 1),
  ( 500.00, 'BILLETE',  2, 1),
  ( 200.00, 'BILLETE',  3, 1),
  ( 100.00, 'BILLETE',  4, 1),
  (  50.00, 'BILLETE',  5, 1),
  (  20.00, 'BILLETE',  6, 1),
  (  10.00, 'MONEDA',   7, 1),
  (   5.00, 'MONEDA',   8, 1),
  (   2.00, 'MONEDA',   9, 1),
  (   1.00, 'MONEDA',  10, 1),
  (   0.50, 'MONEDA',  11, 1),
  (   0.20, 'MONEDA',  12, 1),
  (   0.10, 'MONEDA',  13, 1),
  (   0.01, 'MONEDA',  14, 1);
--rollback DELETE FROM denominacion WHERE valor IN (1000.00, 500.00, 200.00, 100.00, 50.00, 20.00, 10.00, 5.00, 2.00, 1.00, 0.50, 0.20, 0.10, 0.01);
