--liquibase formatted sql

--changeset emm-a:018-1
--comment: Completa los ocho kilatajes obligatorios y las tres hechuras en todos los plazos/sucursales de alhajas, sin sobrescribir filas existentes.
INSERT INTO plazo_hechura_alhaja
    (tabla_prestamo_id, kilataje, precio_base, porc_aumento, precio_prestamo,
     hechura, id_plazo, sucursal_id)
SELECT
    1,
    valores.kilataje,
    ROUND(
        COALESCE(precio.precio_gramo_24k, 0) / 24 * valores.kilataje
        * COALESCE(tabla.porc_prestamo, 0) / 100
        * CASE valores.hechura
            WHEN 'F' THEN COALESCE(precio.factor_fundir, 100)
            WHEN 'N' THEN COALESCE(precio.factor_normal, 100)
            WHEN 'E' THEN COALESCE(precio.factor_especial, 100)
          END / 100,
        4
    ) AS precio_base,
    COALESCE(aumento.porc_aumento, 0) AS porc_aumento,
    ROUND(
        (
            COALESCE(precio.precio_gramo_24k, 0) / 24 * valores.kilataje
            * COALESCE(tabla.porc_prestamo, 0) / 100
            * CASE valores.hechura
                WHEN 'F' THEN COALESCE(precio.factor_fundir, 100)
                WHEN 'N' THEN COALESCE(precio.factor_normal, 100)
                WHEN 'E' THEN COALESCE(precio.factor_especial, 100)
              END / 100
        ) * (1 + COALESCE(aumento.porc_aumento, 0) / 100),
        4
    ) AS precio_prestamo,
    valores.hechura,
    objetivos.id_plazo,
    objetivos.sucursal_id
FROM (
    SELECT pp.plazo_id AS id_plazo, s.id AS sucursal_id
    FROM plazo_prenda pp
    CROSS JOIN sucursal s
    WHERE pp.tipo_prenda_id = 1
    UNION
    SELECT DISTINCT id_plazo, sucursal_id
    FROM plazo_hechura_alhaja
) objetivos
CROSS JOIN (
    SELECT 6 AS kilataje, 'F' AS hechura
    UNION ALL SELECT 6, 'N'
    UNION ALL SELECT 6, 'E'
    UNION ALL SELECT 8, 'F'
    UNION ALL SELECT 8, 'N'
    UNION ALL SELECT 8, 'E'
    UNION ALL SELECT 10, 'F'
    UNION ALL SELECT 10, 'N'
    UNION ALL SELECT 10, 'E'
    UNION ALL SELECT 12, 'F'
    UNION ALL SELECT 12, 'N'
    UNION ALL SELECT 12, 'E'
    UNION ALL SELECT 14, 'F'
    UNION ALL SELECT 14, 'N'
    UNION ALL SELECT 14, 'E'
    UNION ALL SELECT 18, 'F'
    UNION ALL SELECT 18, 'N'
    UNION ALL SELECT 18, 'E'
    UNION ALL SELECT 21, 'F'
    UNION ALL SELECT 21, 'N'
    UNION ALL SELECT 21, 'E'
    UNION ALL SELECT 24, 'F'
    UNION ALL SELECT 24, 'N'
    UNION ALL SELECT 24, 'E'
) valores
LEFT JOIN precio_oro precio
    ON precio.sucursal_id = objetivos.sucursal_id
LEFT JOIN oro_tabla_prestamo tabla
    ON tabla.sucursal_id = objetivos.sucursal_id
   AND tabla.kilataje = valores.kilataje
   AND tabla.hechura = valores.hechura
LEFT JOIN (
    SELECT id_plazo, sucursal_id, hechura, MAX(porc_aumento) AS porc_aumento
    FROM plazo_hechura_alhaja
    GROUP BY id_plazo, sucursal_id, hechura
) aumento
    ON aumento.id_plazo = objetivos.id_plazo
   AND aumento.sucursal_id = objetivos.sucursal_id
   AND aumento.hechura = valores.hechura
WHERE NOT EXISTS (
    SELECT 1
    FROM plazo_hechura_alhaja existente
    WHERE existente.id_plazo = objetivos.id_plazo
      AND existente.sucursal_id = objetivos.sucursal_id
      AND existente.kilataje = valores.kilataje
      AND existente.hechura = valores.hechura
);
