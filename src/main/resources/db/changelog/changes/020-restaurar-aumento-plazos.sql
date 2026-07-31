--liquibase formatted sql

--changeset emm-a:020-1
-- En plazos, el porcentaje es un aumento adicional sobre el precio base.
UPDATE plazo_hechura_alhaja
SET precio_prestamo = ROUND(precio_base * (1 + porc_aumento / 100), 4);
