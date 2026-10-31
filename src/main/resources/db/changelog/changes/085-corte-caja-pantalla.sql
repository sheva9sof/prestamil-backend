--liquibase formatted sql

-- ============================================================================
-- CAJ-07a (PLAN-CORTE-CAJA): pantalla del corte de caja, sin cierre.
-- Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
--
-- El cajero solo ve el corte de hoy, igual que el pase de almoneda (OTR-01). El
-- gerente y Sistemas pueden revisar otro dia: el gerente cierra el dia y Sistemas
-- revisa los dias pendientes (B-04).
-- ============================================================================

--changeset emm-a:085-1
--comment: CAJ-07a - Roles que pueden consultar el corte de caja de otra fecha. CSV de ids de rol; el resto ve siempre el de hoy (fecha del servidor).
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
SELECT 'ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA', '5,1', NULL
WHERE NOT EXISTS (SELECT 1 FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA');
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA';

--changeset emm-a:085-2
--comment: CAJ-07a - Submenu "Corte de caja" bajo "Caja" (idPadre por nombre, como 083).
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT 'Corte de caja', 1, 0, 0, p.`id`, 0, NULL
FROM `opciones` p
WHERE p.`opcion` = 'Caja'
  AND NOT EXISTS (SELECT 1 FROM `opciones` o WHERE o.`opcion` = 'Corte de caja');
--rollback DELETE FROM `opciones` WHERE `opcion` = 'Corte de caja';

--changeset emm-a:085-3
--comment: CAJ-07a - El corte lo ven Sistemas, Cajero y Gerente.
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`)
SELECT a.`idRol`, o.`id`
FROM (
    SELECT 1 AS `idRol`
    UNION ALL SELECT 3
    UNION ALL SELECT 5
) a
JOIN `opciones` o ON o.`opcion` = 'Corte de caja'
LEFT JOIN `roles_opciones` ro
    ON ro.`idRol` = a.`idRol` AND ro.`idOpcion` = o.`id`
WHERE ro.`idRol` IS NULL;
--rollback DELETE ro FROM `roles_opciones` ro JOIN `opciones` o ON o.`id` = ro.`idOpcion` WHERE o.`opcion` = 'Corte de caja';
