--liquibase formatted sql

--changeset emm-a:036-1
--comment: Detalle por ejecucion del pase de almoneda (C-11). Una fila por contrato que cambio a VENCIDO (id_partida NULL: el cambio es de contrato, no de prendas) y una fila por partida que paso a EN_VENTA (id_partida apunta a la prenda que el gerente debe sacar de boveda). La pantalla "Resultados del pase de almoneda" lo consume con pestañas Cartera vencida y Pase a venta.
CREATE TABLE pase_almoneda_detalle (
  id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_pase       BIGINT      NOT NULL,
  id_contrato   BIGINT      NOT NULL,
  id_partida    BIGINT      NULL COMMENT 'Partida que paso a EN_VENTA; NULL para el cambio a VENCIDO (es a nivel contrato)',
  tipo_cambio   VARCHAR(10) NOT NULL COMMENT 'VENCIDO | EN_VENTA',
  CONSTRAINT fk_pase_detalle_pase     FOREIGN KEY (id_pase)     REFERENCES pase_almoneda(id) ON DELETE CASCADE,
  CONSTRAINT fk_pase_detalle_contrato FOREIGN KEY (id_contrato) REFERENCES contrato(id),
  CONSTRAINT fk_pase_detalle_partida  FOREIGN KEY (id_partida)  REFERENCES partida_contrato(id)
);
CREATE INDEX idx_pase_detalle_pase_tipo ON pase_almoneda_detalle (id_pase, tipo_cambio);
--rollback DROP TABLE pase_almoneda_detalle;
