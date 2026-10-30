--liquibase formatted sql

-- ============================================================================
-- CAJ-01c (PLAN-CORTE-CAJA): cajas, denominaciones y traspasos entre cajas.
--
-- RC-01: hay tres tipos de caja (lugar donde esta el dinero): CONCENTRADORA
-- (banco/tesoreria), BOVEDA (caja fuerte de la sucursal) y VENTANILLA (gaveta
-- del cajero).
-- RC-02: cuatro traspasos mueven dinero entre ellas: MINISTRACION
-- (concentradora -> boveda), CONCENTRACION (boveda -> concentradora), DOTACION
-- (boveda -> ventanilla) y RECOLECCION (ventanilla -> boveda).
-- RC-03: cada traspaso lleva su desglose por denominacion.
--
-- H-05: hoy hay un solo turno activo en todo el sistema, asi que cada sucursal
-- tiene UNA sola VENTANILLA (semilla en 074). Por eso turnos NO gana id_caja
-- todavia: la relacion turno-ventanilla se agrega cuando haya turnos simultaneos
-- (B-05). Tampoco hay UNIQUE (id_sucursal, tipo) en caja, para que ese cambio no
-- obligue a tocar el esquema.
--
-- Los traspasos nunca se borran: la cancelacion los marca (estatus CANCELADO),
-- igual que movimiento_contrato (RN-26).
-- ============================================================================

--changeset emm-a:073-1
--comment: CAJ-01c - Cajas de la sucursal (RC-01). La clave es la de COCAE (001, 002, 003).
CREATE TABLE caja (
  id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_sucursal INT          NOT NULL,
  tipo        VARCHAR(20)  NOT NULL COMMENT 'Enum TipoCaja: CONCENTRADORA | BOVEDA | VENTANILLA',
  clave       VARCHAR(10)  NOT NULL COMMENT 'Clave de la caja dentro de la sucursal (001, 002, 003)',
  nombre      VARCHAR(60)  NOT NULL,
  activa      TINYINT(1)   NOT NULL DEFAULT 1,
  CONSTRAINT uq_caja_sucursal_clave UNIQUE (id_sucursal, clave),
  CONSTRAINT fk_caja_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id)
);
--rollback DROP TABLE caja;

--changeset emm-a:073-2
--comment: CAJ-01c - Catalogo de denominaciones del desglose de traspasos y del arqueo (RC-03, Q-10). Global, no por sucursal.
CREATE TABLE denominacion (
  id     INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  valor  DECIMAL(10,2) NOT NULL,
  tipo   VARCHAR(10)   NOT NULL COMMENT 'Enum TipoDenominacion: BILLETE | MONEDA',
  orden  INT           NOT NULL COMMENT 'Orden de captura e impresion: de mayor a menor',
  activa TINYINT(1)    NOT NULL DEFAULT 1,
  CONSTRAINT uq_denominacion_valor UNIQUE (valor)
);
--rollback DROP TABLE denominacion;

--changeset emm-a:073-3
--comment: CAJ-01c - Traspasos de dinero entre cajas (RC-02). El folio sale de FolioService, serie TRASPASO_CAJA, consecutivo por sucursal.
CREATE TABLE traspaso_caja (
  id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_sucursal         INT           NOT NULL,
  folio               BIGINT        NOT NULL COMMENT 'FolioService, serie TRASPASO_CAJA',
  tipo                VARCHAR(20)   NOT NULL COMMENT 'Enum TipoTraspasoCaja: MINISTRACION | CONCENTRACION | DOTACION | RECOLECCION',
  id_caja_origen      INT           NOT NULL,
  id_caja_destino     INT           NOT NULL,
  importe             DECIMAL(18,2) NOT NULL COMMENT 'Suma del desglose (RC-03)',
  fecha_operacion     DATE          NOT NULL COMMENT 'Dia del corte al que pertenece; la pone el servidor',
  fecha_registro      DATETIME      NOT NULL,
  id_usuario          INT           NOT NULL,
  id_turno            INT           NULL COMMENT 'Turno activo al registrar; NULL si se registro sin turno (ministracion/concentracion del gerente)',
  referencia_bancaria VARCHAR(60)   NULL COMMENT 'Ficha bancaria de la concentracion (Q-07), opcional',
  estatus             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO' COMMENT 'Enum EstatusTraspasoCaja: ACTIVO | CANCELADO',
  fecha_cancelacion   DATETIME      NULL,
  id_usuario_cancela  INT           NULL,
  motivo_cancelacion  VARCHAR(300)  NULL,
  CONSTRAINT uq_traspaso_caja_sucursal_folio UNIQUE (id_sucursal, folio),
  CONSTRAINT fk_traspaso_caja_sucursal       FOREIGN KEY (id_sucursal)        REFERENCES sucursal(id),
  CONSTRAINT fk_traspaso_caja_origen         FOREIGN KEY (id_caja_origen)     REFERENCES caja(id),
  CONSTRAINT fk_traspaso_caja_destino        FOREIGN KEY (id_caja_destino)    REFERENCES caja(id),
  CONSTRAINT fk_traspaso_caja_usuario        FOREIGN KEY (id_usuario)         REFERENCES usuarios(id),
  CONSTRAINT fk_traspaso_caja_turno          FOREIGN KEY (id_turno)           REFERENCES turnos(id_turno),
  CONSTRAINT fk_traspaso_caja_usuario_cancela FOREIGN KEY (id_usuario_cancela) REFERENCES usuarios(id)
);
CREATE INDEX idx_traspaso_caja_sucursal_fecha_tipo ON traspaso_caja (id_sucursal, fecha_operacion, tipo);
--rollback DROP TABLE traspaso_caja;

--changeset emm-a:073-4
--comment: CAJ-01c - Desglose por denominacion de cada traspaso (RC-03). Solo se guardan las denominaciones con cantidad.
CREATE TABLE traspaso_caja_denominacion (
  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_traspaso     BIGINT        NOT NULL,
  id_denominacion INT           NOT NULL,
  cantidad        INT           NOT NULL,
  importe         DECIMAL(18,2) NOT NULL COMMENT 'cantidad x valor de la denominacion',
  CONSTRAINT uq_traspaso_caja_denominacion UNIQUE (id_traspaso, id_denominacion),
  CONSTRAINT fk_traspaso_den_traspaso     FOREIGN KEY (id_traspaso)     REFERENCES traspaso_caja(id),
  CONSTRAINT fk_traspaso_den_denominacion FOREIGN KEY (id_denominacion) REFERENCES denominacion(id)
);
--rollback DROP TABLE traspaso_caja_denominacion;
