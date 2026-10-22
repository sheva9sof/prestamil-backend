--liquibase formatted sql

--changeset emm-a:028-1
--comment: Alta del menu principal "Finiquitos y Refrendos" (F2 del plan de Finiquitos/Refrendos). Como el resto del menu, solo se muestra con turno activo.
INSERT INTO `opciones` (`id`, `opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`) VALUES
(17, 'Finiquitos y Refrendos', 1, 1, 0, NULL, 1, 'feather icon-repeat');
--rollback DELETE FROM `opciones` WHERE `id` = 17;

--changeset emm-a:028-2
--comment: Asignar el menu a los mismos roles operativos que Avaluos (Sistemas=1, Cajero=3, Valuador=4, Gerente=5).
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`) VALUES
(1, 17),
(3, 17),
(4, 17),
(5, 17);
--rollback DELETE FROM `roles_opciones` WHERE `idOpcion` = 17;
