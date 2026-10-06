--liquibase formatted sql

--changeset emm-a:033-1
--comment: C-04 - Parametro de sistema para desglosar el IVA de la reposicion de contrato con monto fijo. true (default) interpreta monto_reposicion como IVA incluido (ej. $50 = $43.10 + $6.90); false lo toma como base y suma IVA encima (ej. $50 = $50.00 + $8.00). G-03 queda pendiente con Jorge; el default sigue la lectura mas probable de la reunion 2026-09-30.
INSERT INTO `parametros_sistema` (`id`, `descripcion`, `valor_cadena`, `valor_numerico`, `tipo_dato_interfaz`)
VALUES (18, 'La reposicion de contrato incluye IVA', NULL, 1.00, 'bool');
--rollback DELETE FROM `parametros_sistema` WHERE `id` = 18;
