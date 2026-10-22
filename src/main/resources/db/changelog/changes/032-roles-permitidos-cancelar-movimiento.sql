--liquibase formatted sql

--changeset emm-a:032-1
--comment: Roles autorizados para cancelar un movimiento (F10, RN-26, Jorge 2026-09-25). CSV de ids de rol; el resto de usuarios responde 403. Por defecto Gerente (5); se puede ampliar sin tocar codigo, como ROLES_PERMITIDOS_APERTURA_TURNOS y ROLES_PERMITIDOS_EXENTAR_REPOSICION.
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
VALUES ('ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO', '5', NULL);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO';
