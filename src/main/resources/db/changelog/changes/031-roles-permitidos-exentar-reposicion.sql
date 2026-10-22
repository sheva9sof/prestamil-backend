--liquibase formatted sql

--changeset emm-a:031-1
--comment: Roles autorizados para marcar "No cobrar la reposicion de contrato" (F9, RN- Jorge 2026-09-26). CSV de ids de rol; el resto de usuarios debe cobrar y el backend responde 403. Por defecto Gerente (5) y Sistemas (1); se puede ampliar sin tocar codigo, como ROLES_PERMITIDOS_APERTURA_TURNOS.
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
VALUES ('ROLES_PERMITIDOS_EXENTAR_REPOSICION', '1,5', NULL);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_EXENTAR_REPOSICION';
