--liquibase formatted sql

--changeset emm-a:019-1
-- El porcentaje representa la porcion del precio base que se presta.
UPDATE plazo_hechura_alhaja
SET precio_prestamo = ROUND(precio_base * (porc_aumento / 100), 4);
