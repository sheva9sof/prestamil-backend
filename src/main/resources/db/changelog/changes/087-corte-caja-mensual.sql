--liquibase formatted sql

-- ============================================================================
-- REP-06 (PLAN-VENTAS-CAJA-REPORTES): reporte mensual de corte de caja.
-- Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
--
-- Lo sacan el gerente y Sistemas; el cajero no lo ve (sin menu y 403 en la API).
-- ============================================================================

--changeset emm-a:087-1
--comment: REP-06 - Roles que pueden sacar el reporte mensual de corte de caja. CSV de ids de rol; el resto recibe 403.
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
SELECT 'ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL', '5,1', NULL
WHERE NOT EXISTS (SELECT 1 FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL');
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL';

--changeset emm-a:087-2
--comment: REP-06 - Submenu "Corte de caja mensual" bajo "Caja" (idPadre por nombre, como 083), para Sistemas y Gerente.
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT 'Corte de caja mensual', 1, 0, 0, p.`id`, 0, NULL
FROM `opciones` p
WHERE p.`opcion` = 'Caja'
  AND NOT EXISTS (SELECT 1 FROM `opciones` o WHERE o.`opcion` = 'Corte de caja mensual');
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`)
SELECT a.`idRol`, o.`id`
FROM (
    SELECT 1 AS `idRol`
    UNION ALL SELECT 5
) a
JOIN `opciones` o ON o.`opcion` = 'Corte de caja mensual'
LEFT JOIN `roles_opciones` ro
    ON ro.`idRol` = a.`idRol` AND ro.`idOpcion` = o.`id`
WHERE ro.`idRol` IS NULL;
--rollback DELETE ro FROM `roles_opciones` ro JOIN `opciones` o ON o.`id` = ro.`idOpcion` WHERE o.`opcion` = 'Corte de caja mensual';
--rollback DELETE FROM `opciones` WHERE `opcion` = 'Corte de caja mensual';
