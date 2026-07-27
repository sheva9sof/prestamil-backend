--liquibase formatted sql

--changeset emm-a:021-1
-- Elimina el submenu obsoleto "Parametros prestamo" y sus permisos.
DELETE FROM roles_opciones
WHERE idOpcion IN (
  SELECT id FROM opciones
  WHERE id = 8 OR opcion IN ('Parametros prestamo', 'Parámetros Préstamo')
);

DELETE FROM opciones
WHERE id = 8 OR opcion IN ('Parametros prestamo', 'Parámetros Préstamo');
