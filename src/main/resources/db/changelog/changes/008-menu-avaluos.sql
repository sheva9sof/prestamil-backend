--liquibase formatted sql

--changeset emm-a:008-1
--comment: Agregar opción Avalúos al menú principal
INSERT INTO `opciones` (`id`, `opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`) VALUES
(15, 'Avalúos', 1, 1, 0, NULL, 1, 'feather icon-tag');

--changeset emm-a:008-2
--comment: Asignar opción Avalúos a roles operativos (Sistemas, Cajero, Valuador, Gerente)
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`) VALUES
(1, 15),
(3, 15),
(4, 15),
(5, 15);
