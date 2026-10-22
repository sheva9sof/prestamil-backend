--liquibase formatted sql

--changeset emm-a:034-1
--comment: Tabla minima de flujos de efectivo en caja (C-07, base del corte de caja). Primera incorporacion en C-06: la cancelacion de un empeno del dia registra una ENTRADA por el prestamo devuelto. Los cobros ordinarios y las salidas por devolucion de pago con tarjeta se integraran en C-07.
CREATE TABLE movimiento_caja (
  id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_turno               INT           NOT NULL,
  id_sucursal            INT           NOT NULL,
  tipo                   VARCHAR(10)   NOT NULL COMMENT 'ENTRADA | SALIDA',
  concepto               VARCHAR(120)  NOT NULL,
  monto                  DECIMAL(18,2) NOT NULL,
  id_usuario             INT           NOT NULL,
  id_movimiento_contrato BIGINT        NULL COMMENT 'Movimiento de contrato que origino el flujo; NULL para flujos administrativos',
  fecha                  DATETIME      NOT NULL,
  CONSTRAINT fk_mov_caja_turno    FOREIGN KEY (id_turno)   REFERENCES turnos(id_turno),
  CONSTRAINT fk_mov_caja_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id),
  CONSTRAINT fk_mov_caja_usuario  FOREIGN KEY (id_usuario) REFERENCES usuarios(id),
  CONSTRAINT fk_mov_caja_mov_contrato FOREIGN KEY (id_movimiento_contrato) REFERENCES movimiento_contrato(id)
);
CREATE INDEX idx_mov_caja_sucursal_fecha ON movimiento_caja (id_sucursal, fecha);
CREATE INDEX idx_mov_caja_turno          ON movimiento_caja (id_turno);
--rollback DROP TABLE movimiento_caja;
