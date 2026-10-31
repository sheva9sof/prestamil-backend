--liquibase formatted sql

-- ============================================================================
-- CAJ-06 (PLAN-CORTE-CAJA): saldo de arranque por sucursal y cierre de dia.
-- Decisiones de Jorge (B-04, 2026-10-30):
--   * El saldo inicial lo captura Sistemas por sucursal, una sola vez: es el
--     saldo de la boveda del dia de la migracion, y ese dia es el de arranque.
--   * El gerente cierra el dia en curso. Un dia que quedo pendiente solo lo
--     cierra Sistemas, despues de revisar el corte.
--   * Mientras haya un dia pendiente no se abre turno.
-- Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
--
-- caja_arranque reemplaza a CAJA_FECHA_ARRANQUE / CAJA_SALDO_ARRANQUE de
-- configuraciones (084). Sus valores se pasan como provisionales
-- (confirmado = 0): el corte los sigue usando, pero solo un arranque confirmado
-- por Sistemas permite cerrar dias. fecha_ultimo_cierre es el candado del
-- cierre: todo dia hasta esa fecha queda congelado y las operaciones lo leen
-- con bloqueo de fila.
-- ============================================================================

--changeset emm-a:086-1
--comment: CAJ-06 - Saldo de arranque de caja por sucursal (B-04).
CREATE TABLE caja_arranque (
  id_sucursal         INT           NOT NULL,
  fecha_arranque      DATE          NOT NULL COMMENT 'Dia de la migracion: primer dia con corte',
  saldo_boveda        DECIMAL(18,2) NOT NULL COMMENT 'Saldo de la boveda al abrir el dia de arranque',
  confirmado          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = capturado por Sistemas; ya no cambia',
  id_usuario_captura  INT           NULL,
  fecha_captura       DATETIME      NULL,
  fecha_ultimo_cierre DATE          NULL COMMENT 'Ultimo dia cerrado; hasta aqui todo queda congelado',
  CONSTRAINT pk_caja_arranque PRIMARY KEY (id_sucursal),
  CONSTRAINT fk_caja_arranque_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id),
  CONSTRAINT fk_caja_arranque_usuario FOREIGN KEY (id_usuario_captura) REFERENCES usuarios(id)
);
--rollback DROP TABLE caja_arranque;

--changeset emm-a:086-2
--comment: CAJ-06 - Cierre de dia: el corte congelado en JSON mas sus cifras clave. Un cierre por sucursal y dia.
CREATE TABLE cierre_caja_diario (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  id_sucursal       INT           NOT NULL,
  fecha             DATE          NOT NULL COMMENT 'Dia cerrado',
  saldo_anterior    DECIMAL(18,2) NOT NULL,
  ministraciones    DECIMAL(18,2) NOT NULL,
  concentraciones   DECIMAL(18,2) NOT NULL,
  boveda            DECIMAL(18,2) NOT NULL,
  dotaciones        DECIMAL(18,2) NOT NULL,
  ingresos_efectivo DECIMAL(18,2) NOT NULL COMMENT 'Ingresos de ventanilla sin dotaciones',
  egresos_efectivo  DECIMAL(18,2) NOT NULL COMMENT 'Egresos de ventanilla sin recolecciones',
  recolecciones     DECIMAL(18,2) NOT NULL,
  diferencia        DECIMAL(18,2) NOT NULL COMMENT 'Arqueo de ventanilla (RC-07)',
  saldo_final       DECIMAL(18,2) NOT NULL COMMENT 'Saldo anterior del siguiente dia (RC-08)',
  corte_json        LONGTEXT      NOT NULL COMMENT 'CorteCajaResultado completo al cerrar',
  dia_pendiente     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = lo cerro Sistemas despues de su dia',
  id_usuario        INT           NOT NULL,
  fecha_cierre      DATETIME      NOT NULL,
  CONSTRAINT pk_cierre_caja_diario PRIMARY KEY (id),
  CONSTRAINT uq_cierre_caja_diario_sucursal_fecha UNIQUE (id_sucursal, fecha),
  CONSTRAINT fk_cierre_caja_diario_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id),
  CONSTRAINT fk_cierre_caja_diario_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);
--rollback DROP TABLE cierre_caja_diario;

--changeset emm-a:086-3
--comment: CAJ-06 - Pasa el arranque provisional de configuraciones (084) a caja_arranque, sin confirmar, para cada sucursal.
INSERT INTO caja_arranque (id_sucursal, fecha_arranque, saldo_boveda, confirmado)
SELECT s.id,
       STR_TO_DATE(TRIM(f.valorCadena), '%Y-%m-%d'),
       CAST(TRIM(v.valorCadena) AS DECIMAL(18,2)),
       0
FROM sucursal s
JOIN configuraciones f ON f.configuracion = 'CAJA_FECHA_ARRANQUE'
JOIN configuraciones v ON v.configuracion = 'CAJA_SALDO_ARRANQUE'
WHERE STR_TO_DATE(TRIM(f.valorCadena), '%Y-%m-%d') IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM caja_arranque a WHERE a.id_sucursal = s.id);
--rollback DELETE FROM caja_arranque WHERE confirmado = 0;

--changeset emm-a:086-4
--comment: CAJ-06 - Las dos configuraciones de 084 ya viven en caja_arranque.
DELETE FROM configuraciones WHERE configuracion IN ('CAJA_FECHA_ARRANQUE', 'CAJA_SALDO_ARRANQUE');
--rollback INSERT INTO configuraciones (configuracion, valorCadena, valorEntero) SELECT 'CAJA_FECHA_ARRANQUE', DATE_FORMAT(a.fecha_arranque, '%Y-%m-%d'), NULL FROM caja_arranque a ORDER BY a.id_sucursal LIMIT 1;
--rollback INSERT INTO configuraciones (configuracion, valorCadena, valorEntero) SELECT 'CAJA_SALDO_ARRANQUE', CAST(a.saldo_boveda AS CHAR), NULL FROM caja_arranque a ORDER BY a.id_sucursal LIMIT 1;

--changeset emm-a:086-5
--comment: CAJ-06 - Roles del cierre y del arranque. CSV de ids de rol: el dia en curso lo cierran Gerente y Sistemas; un dia pendiente y el arranque, solo Sistemas.
INSERT INTO `configuraciones` (`configuracion`, `valorCadena`, `valorEntero`)
SELECT s.`configuracion`, s.`valorCadena`, NULL
FROM (
    SELECT 'ROLES_PERMITIDOS_CIERRE_CAJA' AS `configuracion`, '5,1' AS `valorCadena`
    UNION ALL SELECT 'ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE', '1'
    UNION ALL SELECT 'ROLES_PERMITIDOS_ARRANQUE_CAJA', '1'
) s
WHERE NOT EXISTS (SELECT 1 FROM `configuraciones` c WHERE c.`configuracion` = s.`configuracion`);
--rollback DELETE FROM `configuraciones` WHERE `configuracion` IN ('ROLES_PERMITIDOS_CIERRE_CAJA', 'ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE', 'ROLES_PERMITIDOS_ARRANQUE_CAJA');

--changeset emm-a:086-6
--comment: CAJ-06 - Submenu "Arranque de caja" bajo "Caja" (idPadre por nombre, como 083), solo para Sistemas.
INSERT INTO `opciones` (`opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`)
SELECT 'Arranque de caja', 1, 0, 0, p.`id`, 0, NULL
FROM `opciones` p
WHERE p.`opcion` = 'Caja'
  AND NOT EXISTS (SELECT 1 FROM `opciones` o WHERE o.`opcion` = 'Arranque de caja');
INSERT INTO `roles_opciones` (`idRol`, `idOpcion`)
SELECT 1, o.`id`
FROM `opciones` o
LEFT JOIN `roles_opciones` ro ON ro.`idRol` = 1 AND ro.`idOpcion` = o.`id`
WHERE o.`opcion` = 'Arranque de caja'
  AND ro.`idRol` IS NULL;
--rollback DELETE ro FROM `roles_opciones` ro JOIN `opciones` o ON o.`id` = ro.`idOpcion` WHERE o.`opcion` = 'Arranque de caja';
--rollback DELETE FROM `opciones` WHERE `opcion` = 'Arranque de caja';
