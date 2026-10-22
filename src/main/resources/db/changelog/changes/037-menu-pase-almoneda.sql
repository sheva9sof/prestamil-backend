--liquibase formatted sql

--changeset emm-a:037-1
--comment: Menu "Pase de almoneda" (C-11) — pantalla de resultados del pase diario con las dos pestañas (Cartera vencida y Pase a venta). Icono: lista.
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT 'Pase de almoneda', 1, 1, 0, NULL, 1, 'feather icon-clipboard'
WHERE NOT EXISTS (
    SELECT 1 FROM `opciones` WHERE `opcion` = 'Pase de almoneda'
);
--rollback DELETE FROM `opciones` WHERE `opcion` = 'Pase de almoneda';

--changeset emm-a:037-2
--comment: Roles que ven el reporte: Sistemas(1), Cajero(3) y Gerente(5). El cajero necesita Cartera vencida para llamar/mandar WhatsApp; el gerente, Pase a venta para sacar prendas de boveda. Valuador no opera cartera.
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`)
SELECT r.`id`, o.`id`
FROM `roles` r
JOIN `opciones` o ON o.`opcion` = 'Pase de almoneda'
LEFT JOIN `roles_opciones` ro
    ON ro.`idRol` = r.`id` AND ro.`idOpcion` = o.`id`
WHERE r.`id` IN (1, 3, 5)
  AND ro.`idRol` IS NULL;
--rollback DELETE ro FROM `roles_opciones` ro JOIN `opciones` o ON o.`id` = ro.`idOpcion` WHERE o.`opcion` = 'Pase de almoneda' AND ro.`idRol` IN (1, 3, 5);
