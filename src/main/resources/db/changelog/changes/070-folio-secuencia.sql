--liquibase formatted sql

-- ============================================================================
-- CAJ-01a (PLAN-CORTE-CAJA): contador generico de folios por sucursal y serie.
--
-- Mismo patron que folio_nota (029) y folio_bolsa (043): FolioService incrementa
-- el contador con bloqueo de fila dentro de la transaccion de la operacion que
-- usa el folio, asi una operacion que falla no deja huecos y dos operaciones
-- simultaneas no toman el mismo numero. La diferencia es que una sola tabla
-- sirve a todas las series nuevas (enum SerieFolio: NOTA_VENTA, TRASPASO_CAJA,
-- ...) y que la fila se crea en la primera llamada, sin semilla.
--
-- folio_nota, folio_bolsa y folio_codigo_barras NO se migran aqui.
-- ============================================================================

--changeset emm-a:070-1
--comment: CAJ-01a - Ultimo folio emitido por sucursal y serie. Para continuar una numeracion externa basta con ajustar ultimo_folio.
CREATE TABLE folio_secuencia (
  id_sucursal  INT         NOT NULL,
  serie        VARCHAR(30) NOT NULL COMMENT 'Nombre del enum SerieFolio',
  ultimo_folio BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT pk_folio_secuencia PRIMARY KEY (id_sucursal, serie),
  CONSTRAINT fk_folio_secuencia_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id)
);
--rollback DROP TABLE folio_secuencia;
