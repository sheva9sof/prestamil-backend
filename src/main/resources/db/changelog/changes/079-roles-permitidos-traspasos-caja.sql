--liquibase formatted sql

-- ============================================================================
-- CAJ-02 (PLAN-CORTE-CAJA): roles que pueden registrar cada traspaso entre cajas
-- (RC-02) y cancelarlos. CSV de ids de rol, igual que ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO.
-- Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
--
-- Ministracion y concentracion mueven dinero de la boveda con tesoreria y el banco:
-- gerente y Sistemas (Q-06). Dotacion y recoleccion mueven dinero entre la boveda y
-- la ventanilla: tambien el cajero.
-- Cancelar: solo Sistemas mientras no exista la autorizacion con credenciales de
-- VTA-03 (TODO A-05 en TraspasoCajaService).
-- ============================================================================

--changeset emm-a:079-1
--comment: CAJ-02 - Roles por tipo de traspaso entre cajas (Q-06) y para cancelarlos (A-05).
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`) VALUES
  ('ROLES_PERMITIDOS_MINISTRACION',      '5,1',   NULL),
  ('ROLES_PERMITIDOS_CONCENTRACION',     '5,1',   NULL),
  ('ROLES_PERMITIDOS_DOTACION',          '5,3,1', NULL),
  ('ROLES_PERMITIDOS_RECOLECCION',       '5,3,1', NULL),
  ('ROLES_PERMITIDOS_CANCELAR_TRASPASO', '1',     NULL);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` IN ('ROLES_PERMITIDOS_MINISTRACION', 'ROLES_PERMITIDOS_CONCENTRACION', 'ROLES_PERMITIDOS_DOTACION', 'ROLES_PERMITIDOS_RECOLECCION', 'ROLES_PERMITIDOS_CANCELAR_TRASPASO');
