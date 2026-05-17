--liquibase formatted sql

--changeset emmanuel:003-session-params comment:Add session timeout configurable parameters

INSERT INTO `parametros_sistema` (`id`, `descripcion`, `valor_cadena`, `valor_numerico`, `tipo_dato_interfaz`) VALUES
(16, 'Tiempo de sesión (minutos)', NULL, 30.00, 'number'),
(17, 'Minutos de aviso antes de expirar sesión', NULL, 3.00, 'number');
