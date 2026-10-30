--liquibase formatted sql

--changeset emm-a:075-1
--comment: OTR-01 (PLAN-VENTAS-CAJA-REPORTES, Jorge 01:17:16) - Roles que pueden consultar y exportar el pase de almoneda de otra fecha. CSV de ids de rol, igual que ROLES_PERMITIDOS_CATALOGO_PRENDAS. Por defecto solo Sistemas (1); cajero y gerente solo ven el pase de hoy (fecha del servidor).
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
VALUES ('ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA', '1', NULL);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` = 'ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA';
