--liquibase formatted sql

--changeset emm-a:035-1
--comment: Bitacora del pase de almoneda diario (F11). Clave unica (sucursal, fecha) para que el pase sea idempotente: si ya corrio hoy en esta sucursal, no se vuelve a ejecutar. Guarda cuantos contratos cambiaron y el prestamo total que paso a venta para que el futuro modulo de boveda lo consuma.
CREATE TABLE pase_almoneda (
  id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_sucursal             INT           NOT NULL,
  fecha                   DATE          NOT NULL,
  ejecutado_en            DATETIME      NOT NULL COMMENT 'Timestamp exacto de la ejecucion (hora)',
  contratos_a_vencido     INT           NOT NULL DEFAULT 0 COMMENT 'VIGENTE/EN_GRACIA -> VENCIDO cuando hoy > venc + gracia',
  contratos_a_venta       INT           NOT NULL DEFAULT 0 COMMENT 'VENCIDO -> EN_VENTA cuando hoy >= fecha_comercializacion (RN-08)',
  monto_pasado_a_venta    DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'Suma de monto_prestamo de los contratos pasados a venta hoy; para boveda',
  CONSTRAINT uq_pase_almoneda_sucursal_fecha UNIQUE (id_sucursal, fecha),
  CONSTRAINT fk_pase_almoneda_sucursal       FOREIGN KEY (id_sucursal) REFERENCES sucursal(id)
);
--rollback DROP TABLE pase_almoneda;
