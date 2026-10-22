--liquibase formatted sql

--changeset emm-a:039-1
--comment: D-14 - Parametro de sistema con el porcentaje de sancion extemporanea semanal por default. Se aplica cuando al guardar un PlazoParametro no se envia el campo porcSancionSemanal (fila nueva o fila existente con valor en 0). ParametrosSistemaCache.getPorcSancionSemanalDefault lo lee; fallback a 2 con log.warn si el registro falta.
INSERT INTO `parametros_sistema` (`id`, `descripcion`, `valor_cadena`, `valor_numerico`, `tipo_dato_interfaz`)
VALUES (19, 'Porcentaje sancion semanal (default)', NULL, 2.00, 'number');
--rollback DELETE FROM `parametros_sistema` WHERE `id` = 19;
