--liquibase formatted sql

-- ============================================================================
-- CAJ-01d (PLAN-CORTE-CAJA, Q-06, Jorge 2026-10-30): la ministracion la puede
-- registrar el cajero o el gerente. Se agrega el cajero (3) a
-- ROLES_PERMITIDOS_MINISTRACION. Va en un changeset nuevo porque 079 ya esta
-- aplicado. Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
-- ============================================================================

--changeset emm-a:080-1
--comment: CAJ-01d - Q-06: el cajero tambien registra ministraciones (5,1 -> 5,3,1).
UPDATE `configuraciones` SET `valorCadena` = '5,3,1'
WHERE `configuracion` = 'ROLES_PERMITIDOS_MINISTRACION';
--rollback UPDATE `configuraciones` SET `valorCadena` = '5,1' WHERE `configuracion` = 'ROLES_PERMITIDOS_MINISTRACION';
