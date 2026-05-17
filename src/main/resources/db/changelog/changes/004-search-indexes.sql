--liquibase formatted sql

--changeset emmanuel:004-fulltext-clientes comment:FULLTEXT index on clientes(nombre, apellido_paterno, apellido_materno) for MATCH/AGAINST queries
ALTER TABLE `clientes`
  ADD FULLTEXT INDEX `ft_clientes_nombre_completo` (`nombre`, `apellido_paterno`, `apellido_materno`);
--rollback ALTER TABLE `clientes` DROP INDEX `ft_clientes_nombre_completo`;
