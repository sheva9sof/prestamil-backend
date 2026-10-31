--liquibase formatted sql

-- ============================================================================
-- CAJ-04 (PLAN-CORTE-CAJA): menu "Caja" con un submenu por traspaso. Se inserta
-- por nombre (como 037) para no chocar con los ids de Salvador. Los roles de
-- cada submenu son los de 079 con el ajuste de 080 (Q-06): 1 Sistemas,
-- 3 Cajero, 5 Gerente. El backend vuelve a validar el rol al registrar.
-- ============================================================================

--changeset emm-a:083-1
--comment: CAJ-04 - Menu padre "Caja". Icono: maletin (feather icon-briefcase).
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT 'Caja', 1, 1, 0, NULL, 1, 'feather icon-briefcase'
WHERE NOT EXISTS (
    SELECT 1 FROM `opciones` WHERE `opcion` = 'Caja'
);
--rollback DELETE FROM `opciones` WHERE `opcion` = 'Caja';

--changeset emm-a:083-2
--comment: CAJ-04 - Submenus de traspasos bajo "Caja" (idPadre por nombre).
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT s.`opcion`, 1, 0, 0, p.`id`, 0, NULL
FROM (
    SELECT 'Ministración' AS `opcion`
    UNION ALL SELECT 'Concentración'
    UNION ALL SELECT 'Dotación'
    UNION ALL SELECT 'Recolección'
) s
JOIN `opciones` p ON p.`opcion` = 'Caja'
WHERE NOT EXISTS (
    SELECT 1 FROM `opciones` o WHERE o.`opcion` = s.`opcion`
);
--rollback DELETE FROM `opciones` WHERE `opcion` IN ('Ministración', 'Concentración', 'Dotación', 'Recolección');

--changeset emm-a:083-3
--comment: CAJ-04 - Roles: el menu padre lo ven los tres; concentracion solo Sistemas y Gerente (079); ministracion, dotacion y recoleccion tambien el Cajero (079, 080).
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`)
SELECT a.`idRol`, o.`id`
FROM (
    SELECT 1 AS `idRol`, 'Caja' AS `opcion`
    UNION ALL SELECT 3, 'Caja'
    UNION ALL SELECT 5, 'Caja'
    UNION ALL SELECT 1, 'Ministración'
    UNION ALL SELECT 3, 'Ministración'
    UNION ALL SELECT 5, 'Ministración'
    UNION ALL SELECT 1, 'Concentración'
    UNION ALL SELECT 5, 'Concentración'
    UNION ALL SELECT 1, 'Dotación'
    UNION ALL SELECT 3, 'Dotación'
    UNION ALL SELECT 5, 'Dotación'
    UNION ALL SELECT 1, 'Recolección'
    UNION ALL SELECT 3, 'Recolección'
    UNION ALL SELECT 5, 'Recolección'
) a
JOIN `opciones` o ON o.`opcion` = a.`opcion`
LEFT JOIN `roles_opciones` ro
    ON ro.`idRol` = a.`idRol` AND ro.`idOpcion` = o.`id`
WHERE ro.`idRol` IS NULL;
--rollback DELETE ro FROM `roles_opciones` ro JOIN `opciones` o ON o.`id` = ro.`idOpcion` WHERE o.`opcion` IN ('Caja', 'Ministración', 'Concentración', 'Dotación', 'Recolección');
