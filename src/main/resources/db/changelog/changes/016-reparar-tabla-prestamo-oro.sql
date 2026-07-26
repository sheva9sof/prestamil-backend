--liquibase formatted sql

--changeset emm-a:016-1
--comment: Completa las 24 celdas COCAE para toda sucursal existente sin sobrescribir valores editados.
INSERT INTO oro_tabla_prestamo
    (sucursal_id, kilataje, hechura, porc_prestamo, actualizado_en)
SELECT
    s.id,
    valores.kilataje,
    valores.hechura,
    valores.porc_prestamo,
    NOW()
FROM sucursal s
CROSS JOIN (
    SELECT 6 AS kilataje,  'F' AS hechura, 24.7600 AS porc_prestamo
    UNION ALL SELECT 6,  'N', 26.7900
    UNION ALL SELECT 6,  'E', 30.7300
    UNION ALL SELECT 8,  'F', 60.1100
    UNION ALL SELECT 8,  'N', 62.0400
    UNION ALL SELECT 8,  'E', 64.1000
    UNION ALL SELECT 10, 'F', 61.0500
    UNION ALL SELECT 10, 'N', 62.6300
    UNION ALL SELECT 10, 'E', 64.1500
    UNION ALL SELECT 12, 'F', 61.6300
    UNION ALL SELECT 12, 'N', 62.9500
    UNION ALL SELECT 12, 'E', 64.2400
    UNION ALL SELECT 14, 'F', 62.1400
    UNION ALL SELECT 14, 'N', 63.2700
    UNION ALL SELECT 14, 'E', 64.3900
    UNION ALL SELECT 18, 'F', 62.5200
    UNION ALL SELECT 18, 'N', 63.4000
    UNION ALL SELECT 18, 'E', 66.3400
    UNION ALL SELECT 21, 'F', 62.6700
    UNION ALL SELECT 21, 'N', 63.4400
    UNION ALL SELECT 21, 'E', 66.0800
    UNION ALL SELECT 24, 'F', 0.0000
    UNION ALL SELECT 24, 'N', 0.0000
    UNION ALL SELECT 24, 'E', 0.0000
) valores
WHERE NOT EXISTS (
    SELECT 1
    FROM oro_tabla_prestamo existente
    WHERE existente.sucursal_id = s.id
      AND existente.kilataje = valores.kilataje
      AND existente.hechura = valores.hechura
);
