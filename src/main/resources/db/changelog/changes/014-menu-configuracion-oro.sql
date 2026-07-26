--liquibase formatted sql

--changeset emm-a:014-1
--comment: Alta del submenu "Configuracion del Oro" bajo el menu Configuracion (idPadre=6).
INSERT INTO `opciones` (`id`, `opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`) VALUES
(16, 'Configuración del Oro', 1, 0, 0, 6, 0, NULL);
--rollback DELETE FROM `opciones` WHERE `id` = 16;

--changeset emm-a:014-2
--comment: Asignar el submenu a los roles que ya ven Configuracion (Sistemas=1, Gerente=5).
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`) VALUES
(1, 16),
(5, 16);
--rollback DELETE FROM `roles_opciones` WHERE `idOpcion` = 16;
