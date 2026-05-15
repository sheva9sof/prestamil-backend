--liquibase formatted sql

--changeset emmanuel:005-drop-session-token-usuarios comment:Drop unused session_token column from usuarios (vestigial from abandoned JWT design)
ALTER TABLE `usuarios` DROP COLUMN `session_token`;
--rollback ALTER TABLE `usuarios` ADD COLUMN `session_token` varchar(100) DEFAULT NULL AFTER `editable`;
