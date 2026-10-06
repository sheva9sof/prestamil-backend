--liquibase formatted sql

--changeset emm-a:038-1
--comment: Eliminar definitivamente el submenu obsoleto "Parametros de Prestamo" y sus permisos en bases donde el changeset 021 ya fue marcado como ejecutado.
DELETE ro
FROM `roles_opciones` ro
JOIN `opciones` o ON o.`id` = ro.`idOpcion`
WHERE o.`id` = 8
   OR o.`opcion` IN (
       'Parametros prestamo',
       'Parametros de Prestamo',
       'Parámetros Préstamo',
       'Parámetros de Préstamo'
   );

DELETE FROM `opciones`
WHERE `id` = 8
   OR `opcion` IN (
       'Parametros prestamo',
       'Parametros de Prestamo',
       'Parámetros Préstamo',
       'Parámetros de Préstamo'
   );

