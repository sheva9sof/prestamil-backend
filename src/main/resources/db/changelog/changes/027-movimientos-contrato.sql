--liquibase formatted sql

--changeset emm-a:027-1
--comment: Estatus de contrato: DESEMPENADO se renombra a FINIQUITADO (nombre usado por COCAE y por el plan de Finiquitos/Refrendos). Se agregan VENDIDO y CANCELADO en el enum Java; EN_GRACIA no se persiste, se deriva por fecha (F1).
UPDATE contrato SET estatus = 'FINIQUITADO' WHERE estatus = 'DESEMPENADO';
--rollback UPDATE contrato SET estatus = 'DESEMPENADO' WHERE estatus = 'FINIQUITADO';

--changeset emm-a:027-2
--comment: Saldo capital, fecha de contrato vigente (RN-02) y fecha de comercializacion (RN-08). Se agregan NULL para poder hacer el backfill; el changeset 027-4 las vuelve NOT NULL.
ALTER TABLE contrato
  ADD COLUMN saldo_capital          DECIMAL(18,2) NULL COMMENT 'Saldo de capital vigente: base de interes y sancion (RN-09)' AFTER monto_avaluo,
  ADD COLUMN fecha_contrato         DATE          NULL COMMENT 'Inicio del periodo vigente; cambia con cada refrendo (RN-02)' AFTER fecha_apertura,
  ADD COLUMN fecha_comercializacion DATE          NULL COMMENT 'Vencimiento + 15 dias; antes de esta fecha la prenda no se puede vender (RN-08)' AFTER fecha_vencimiento;
--rollback ALTER TABLE contrato DROP COLUMN saldo_capital, DROP COLUMN fecha_contrato, DROP COLUMN fecha_comercializacion;

--changeset emm-a:027-3
--comment: Backfill de contrato. saldo = prestamo - abonos a capital registrados (0 si esta finiquitado); fecha_contrato = vencimiento - duracion del plazo; comercializacion = vencimiento + 15.
UPDATE contrato c
  JOIN plazo p ON p.id = c.id_plazo
   SET c.saldo_capital = CASE
                           WHEN c.estatus = 'FINIQUITADO' THEN 0.00
                           ELSE c.monto_prestamo - COALESCE(
                                  (SELECT SUM(m.abono_capital) FROM movimiento_contrato m WHERE m.id_contrato = c.id), 0.00)
                         END,
       c.fecha_contrato = DATE_SUB(c.fecha_vencimiento, INTERVAL (p.dias_por_periodo * p.numero_periodos) DAY),
       c.fecha_comercializacion = DATE_ADD(c.fecha_vencimiento, INTERVAL 15 DAY);
--rollback UPDATE contrato SET saldo_capital = NULL, fecha_contrato = NULL, fecha_comercializacion = NULL;

--changeset emm-a:027-4
--comment: saldo_capital y fecha_contrato obligatorios tras el backfill + indices para el listado de operacion (F2) y el pase diario (F11)
ALTER TABLE contrato
  MODIFY COLUMN saldo_capital  DECIMAL(18,2) NOT NULL COMMENT 'Saldo de capital vigente: base de interes y sancion (RN-09)',
  MODIFY COLUMN fecha_contrato DATE          NOT NULL COMMENT 'Inicio del periodo vigente; cambia con cada refrendo (RN-02)';
CREATE INDEX idx_contrato_sucursal_estatus ON contrato (id_sucursal, estatus);
CREATE INDEX idx_contrato_fecha_vencimiento ON contrato (fecha_vencimiento);
CREATE INDEX idx_contrato_fecha_comercializacion ON contrato (fecha_comercializacion);
--rollback DROP INDEX idx_contrato_fecha_comercializacion ON contrato; DROP INDEX idx_contrato_fecha_vencimiento ON contrato; DROP INDEX idx_contrato_sucursal_estatus ON contrato; ALTER TABLE contrato MODIFY COLUMN saldo_capital DECIMAL(18,2) NULL, MODIFY COLUMN fecha_contrato DATE NULL;

--changeset emm-a:027-5
--comment: Catalogo de bancos emisores para pagos con tarjeta (RN-24). Administrable desde Parametros Generales (F3); sin borrado fisico si ya tiene movimientos.
CREATE TABLE banco (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL,
  activo  TINYINT(1)  NOT NULL DEFAULT 1,
  CONSTRAINT uq_banco_nombre UNIQUE (nombre)
);
INSERT INTO banco (nombre) VALUES
  ('BBVA'), ('Santander'), ('Banorte'), ('Banamex'), ('HSBC'), ('Scotiabank'),
  ('Banco Azteca'), ('BanCoppel'), ('Inbursa'), ('Banregio'), ('Afirme'), ('Otro');
--rollback DROP TABLE banco;

--changeset emm-a:027-6
--comment: movimiento_contrato: desglose, forma de pago, estado antes/despues (para la cancelacion generica de F10), cancelacion e idempotencia. "monto" sigue siendo el total cobrado (no se agrega columna total) y "semanas_vencidas" sigue siendo los periodos extemporaneos (no se duplica).
ALTER TABLE movimiento_contrato
  ADD COLUMN periodos_normales       INT           NULL COMMENT 'Periodos normales cubiertos' AFTER semanas_vencidas,
  ADD COLUMN dias_gracia_usados      INT           NOT NULL DEFAULT 0 COMMENT 'Columna Dias G de COCAE (auditoria)' AFTER periodos_normales,
  ADD COLUMN interes_por_periodo     DECIMAL(18,4) NULL COMMENT 'Interes + almacenaje de un periodo, sin redondear (RN-10)' AFTER dias_gracia_usados,
  ADD COLUMN porc_descuento_interes  DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT 'Descuento s/interes parametrizado al momento de la operacion (RN-27)' AFTER interes_por_periodo,
  ADD COLUMN importe_descuento       DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER porc_descuento_interes,
  ADD COLUMN iva                     DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'IVA sobre interes + sancion (RN-12)' AFTER importe_descuento,
  ADD COLUMN importe_efectivo        DECIMAL(18,2) NULL AFTER iva,
  ADD COLUMN importe_tarjeta         DECIMAL(18,2) NULL AFTER importe_efectivo,
  ADD COLUMN tipo_tarjeta            VARCHAR(10)   NULL COMMENT 'CREDITO | DEBITO' AFTER importe_tarjeta,
  ADD COLUMN tarjeta_ultimos4        CHAR(4)       NULL COMMENT 'Solo ultimos 4 digitos (PCI DSS)' AFTER tipo_tarjeta,
  ADD COLUMN id_banco_emisor         INT           NULL AFTER tarjeta_ultimos4,
  ADD COLUMN autorizacion_banco      VARCHAR(30)   NULL AFTER id_banco_emisor,
  ADD COLUMN cambio_entregado        DECIMAL(18,2) NULL AFTER autorizacion_banco,
  ADD COLUMN saldo_anterior          DECIMAL(18,2) NULL AFTER cambio_entregado,
  ADD COLUMN saldo_nuevo             DECIMAL(18,2) NULL AFTER saldo_anterior,
  ADD COLUMN fecha_contrato_anterior DATE          NULL AFTER saldo_nuevo,
  ADD COLUMN fecha_venc_anterior     DATE          NULL AFTER fecha_contrato_anterior,
  ADD COLUMN fecha_contrato_nueva    DATE          NULL AFTER fecha_venc_anterior,
  ADD COLUMN fecha_venc_nueva        DATE          NULL AFTER fecha_contrato_nueva,
  ADD COLUMN estatus_anterior        VARCHAR(20)   NULL AFTER fecha_venc_nueva,
  ADD COLUMN estatus_nuevo           VARCHAR(20)   NULL AFTER estatus_anterior,
  ADD COLUMN num_refrendos_anterior  INT           NULL AFTER estatus_nuevo,
  ADD COLUMN cancelado               TINYINT(1)    NOT NULL DEFAULT 0 AFTER num_refrendos_anterior,
  ADD COLUMN fecha_cancelacion       DATETIME      NULL AFTER cancelado,
  ADD COLUMN id_usuario_cancela      INT           NULL AFTER fecha_cancelacion,
  ADD COLUMN motivo_cancelacion      VARCHAR(300)  NULL AFTER id_usuario_cancela,
  ADD COLUMN request_id              VARCHAR(36)   NULL COMMENT 'Idempotencia: evita doble cobro por doble clic' AFTER motivo_cancelacion,
  ADD CONSTRAINT uq_mov_request_id    UNIQUE (request_id),
  ADD CONSTRAINT fk_mov_banco_emisor  FOREIGN KEY (id_banco_emisor)    REFERENCES banco(id),
  ADD CONSTRAINT fk_mov_usuario_cancela FOREIGN KEY (id_usuario_cancela) REFERENCES usuarios(id);
CREATE INDEX idx_mov_contrato_fecha ON movimiento_contrato (id_contrato, fecha);
--rollback DROP INDEX idx_mov_contrato_fecha ON movimiento_contrato; ALTER TABLE movimiento_contrato DROP FOREIGN KEY fk_mov_usuario_cancela, DROP FOREIGN KEY fk_mov_banco_emisor, DROP INDEX uq_mov_request_id, DROP COLUMN periodos_normales, DROP COLUMN dias_gracia_usados, DROP COLUMN interes_por_periodo, DROP COLUMN porc_descuento_interes, DROP COLUMN importe_descuento, DROP COLUMN iva, DROP COLUMN importe_efectivo, DROP COLUMN importe_tarjeta, DROP COLUMN tipo_tarjeta, DROP COLUMN tarjeta_ultimos4, DROP COLUMN id_banco_emisor, DROP COLUMN autorizacion_banco, DROP COLUMN cambio_entregado, DROP COLUMN saldo_anterior, DROP COLUMN saldo_nuevo, DROP COLUMN fecha_contrato_anterior, DROP COLUMN fecha_venc_anterior, DROP COLUMN fecha_contrato_nueva, DROP COLUMN fecha_venc_nueva, DROP COLUMN estatus_anterior, DROP COLUMN estatus_nuevo, DROP COLUMN num_refrendos_anterior, DROP COLUMN cancelado, DROP COLUMN fecha_cancelacion, DROP COLUMN id_usuario_cancela, DROP COLUMN motivo_cancelacion, DROP COLUMN request_id;

--changeset emm-a:027-7
--comment: Tipos de movimiento a codigo corto (seccion 6 del plan). Un refrendo con abono a capital pasa a RC; ABONO suelto (nunca lo genero el sistema) tambien se asimila a RC.
UPDATE movimiento_contrato
   SET tipo = CASE
                WHEN tipo = 'REFRENDO' AND abono_capital > 0 THEN 'RC'
                WHEN tipo = 'REFRENDO'               THEN 'RF'
                WHEN tipo = 'REFRENDO_EXTEMPORANEO'  THEN 'RX'
                WHEN tipo = 'FINIQUITO'              THEN 'FI'
                WHEN tipo = 'FINIQUITO_EXTEMPORANEO' THEN 'FX'
                WHEN tipo = 'ABONO'                  THEN 'RC'
                WHEN tipo = 'REPOSICION_CONTRATO'    THEN 'RE'
                ELSE tipo
              END;
--rollback UPDATE movimiento_contrato SET tipo = CASE WHEN tipo IN ('RF','RC') THEN 'REFRENDO' WHEN tipo = 'RX' THEN 'REFRENDO_EXTEMPORANEO' WHEN tipo = 'FI' THEN 'FINIQUITO' WHEN tipo = 'FX' THEN 'FINIQUITO_EXTEMPORANEO' WHEN tipo = 'RE' THEN 'REPOSICION_CONTRATO' ELSE tipo END;

--changeset emm-a:027-8
--comment: Backfill del IVA de movimientos existentes. refrendar guarda monto = interes (+almacen+gastos) + sancion + IVA + abono, asi que IVA = monto - interes - sancion - abono. Los refrendos anteriores a la Pasada 2 no cobraban IVA y quedan en 0. Reposicion no lleva IVA.
UPDATE movimiento_contrato
   SET iva = GREATEST(monto - COALESCE(interes, 0.00) - sancion - abono_capital, 0.00)
 WHERE tipo IN ('RF', 'RC', 'RX');
--rollback UPDATE movimiento_contrato SET iva = 0.00;

--changeset emm-a:027-9
--comment: Movimiento EMP (empeno, periodo 0) para cada contrato que no lo tenga. monto = prestamo entregado; fechas nuevas = las originales del empeno (apertura + duracion del plazo), no las vigentes, porque el EMP describe el estado al empenar.
INSERT INTO movimiento_contrato
  (id_contrato, id_turno, id_usuario, tipo, monto, interes, sancion, abono_capital, semanas_vencidas,
   periodos_normales, iva, saldo_nuevo, fecha_contrato_nueva, fecha_venc_nueva, estatus_nuevo,
   fecha, observaciones)
SELECT c.id, c.id_turno, c.id_usuario, 'EMP', c.monto_prestamo, 0.00, 0.00, 0.00, 0,
       0, 0.00, c.monto_prestamo, DATE(c.fecha_apertura),
       DATE_ADD(DATE(c.fecha_apertura), INTERVAL (p.dias_por_periodo * p.numero_periodos) DAY), 'VIGENTE',
       c.fecha_apertura, 'Empeño (backfill changeset 027)'
  FROM contrato c
  JOIN plazo p ON p.id = c.id_plazo
 WHERE NOT EXISTS (SELECT 1 FROM movimiento_contrato m WHERE m.id_contrato = c.id AND m.tipo = 'EMP');
--rollback DELETE FROM movimiento_contrato WHERE tipo = 'EMP' AND observaciones = 'Empeño (backfill changeset 027)';

--changeset emm-a:027-10
--comment: Estatus por partida (columna St de COCAE): OP en operacion, FIN finiquitada, VEN vendida, APA apartada. Una partida VEN o APA bloquea refrendo y finiquito de todo el contrato (RN-17).
ALTER TABLE partida_contrato
  ADD COLUMN estatus VARCHAR(10) NOT NULL DEFAULT 'OP' AFTER estado_fisico;
UPDATE partida_contrato pc
  JOIN contrato c ON c.id = pc.id_contrato
   SET pc.estatus = 'FIN'
 WHERE c.estatus = 'FINIQUITADO';
--rollback ALTER TABLE partida_contrato DROP COLUMN estatus;
