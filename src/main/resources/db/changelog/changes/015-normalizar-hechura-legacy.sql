--liquibase formatted sql

--changeset emm-a:015-1
--comment: Normaliza codigos de hechura legacy 'HF'/'HN'/'HE' (plazo_hechura_alhaja, sembrados
--          en 002-initial-data.sql para id_plazo=1) a la convencion de un caracter 'F'/'N'/'E'
--          ya usada por oro_tabla_prestamo (changeset 012) y por los plazos creados via
--          "Inicializar tabla estandar" (id_plazo 5/6). Sin esta normalizacion,
--          PlazoService.recalcularRegistros nunca encuentra la celda de %Prestamo para
--          id_plazo=1 (busca "kilataje-HF" en un mapa cuyas claves son "kilataje-F") y lanza
--          ResourceNotFoundException en cualquier recalculo real (precio del gramo o edicion
--          de %Prestamo en la pantalla Configuracion del Oro).
UPDATE plazo_hechura_alhaja SET hechura = 'F' WHERE id_plazo = 1 AND hechura = 'HF';
UPDATE plazo_hechura_alhaja SET hechura = 'N' WHERE id_plazo = 1 AND hechura = 'HN';
UPDATE plazo_hechura_alhaja SET hechura = 'E' WHERE id_plazo = 1 AND hechura = 'HE';
--rollback UPDATE plazo_hechura_alhaja SET hechura = 'HF' WHERE id_plazo = 1 AND hechura = 'F';
--rollback UPDATE plazo_hechura_alhaja SET hechura = 'HN' WHERE id_plazo = 1 AND hechura = 'N';
--rollback UPDATE plazo_hechura_alhaja SET hechura = 'HE' WHERE id_plazo = 1 AND hechura = 'E';
