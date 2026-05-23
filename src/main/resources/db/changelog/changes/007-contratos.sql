--liquibase formatted sql

--changeset emm-a:007-1
--comment: Crear tabla contrato — cabecera de cada contrato de empeño
CREATE TABLE contrato (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  folio               VARCHAR(20) UNIQUE,
  id_cliente          INT NOT NULL,
  id_turno            INT NOT NULL,
  id_sucursal         INT NOT NULL DEFAULT 1,
  id_plazo            INT NOT NULL,
  id_usuario          INT NOT NULL,
  id_beneficiario     INT,
  nombre_beneficiario VARCHAR(200),
  tipo_identificacion VARCHAR(60),
  num_identificacion  VARCHAR(30),
  fecha_apertura      DATETIME NOT NULL,
  fecha_vencimiento   DATE NOT NULL,
  monto_prestamo      DECIMAL(18,2) NOT NULL,
  monto_avaluo        DECIMAL(18,2) NOT NULL,
  estatus             VARCHAR(20) NOT NULL DEFAULT 'VIGENTE',
  num_refrendos       INT NOT NULL DEFAULT 0,
  creado_en           DATETIME NOT NULL,
  actualizado_en      DATETIME NOT NULL,
  CONSTRAINT fk_contrato_cliente   FOREIGN KEY (id_cliente)     REFERENCES clientes(id),
  CONSTRAINT fk_contrato_turno     FOREIGN KEY (id_turno)       REFERENCES turnos(id_turno),
  CONSTRAINT fk_contrato_sucursal  FOREIGN KEY (id_sucursal)    REFERENCES sucursal(id),
  CONSTRAINT fk_contrato_plazo     FOREIGN KEY (id_plazo)       REFERENCES plazo(id),
  CONSTRAINT fk_contrato_usuario   FOREIGN KEY (id_usuario)     REFERENCES usuarios(id)
);

--changeset emm-a:007-2
--comment: Crear tabla partida_contrato — prendas dentro de cada contrato
CREATE TABLE partida_contrato (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_contrato         BIGINT NOT NULL,
  num_partida         INT NOT NULL,
  id_tipo_prenda      INT NOT NULL,
  id_valor_prenda     INT,
  clave_prenda        VARCHAR(20),
  descripcion         VARCHAR(200) NOT NULL,
  cantidad            INT NOT NULL DEFAULT 1,
  peso_gramos         DECIMAL(10,4),
  kilataje            INT,
  hechura             VARCHAR(5),
  precio_x_gramo      DECIMAL(12,4),
  avaluo_real         DECIMAL(18,2) NOT NULL,
  avaluo_contrato     DECIMAL(18,2) NOT NULL,
  monto_prestamo      DECIMAL(18,2) NOT NULL,
  subtipo             VARCHAR(50),
  marca               VARCHAR(80),
  modelo              VARCHAR(80),
  serie_imei          VARCHAR(60),
  estado_fisico       VARCHAR(20),
  CONSTRAINT fk_partida_contrato  FOREIGN KEY (id_contrato)     REFERENCES contrato(id),
  CONSTRAINT fk_partida_tipo      FOREIGN KEY (id_tipo_prenda)  REFERENCES tipo_prenda(id),
  CONSTRAINT fk_partida_valor     FOREIGN KEY (id_valor_prenda) REFERENCES cat_valor_prenda(id_valor_atributo)
);

--changeset emm-a:007-3
--comment: Crear tabla movimiento_contrato — historial de pagos, refrendos y finiquitos
CREATE TABLE movimiento_contrato (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_contrato     BIGINT NOT NULL,
  id_turno        INT NOT NULL,
  id_usuario      INT NOT NULL,
  tipo            VARCHAR(20) NOT NULL,
  monto           DECIMAL(18,2) NOT NULL,
  interes         DECIMAL(18,2),
  fecha           DATETIME NOT NULL,
  observaciones   VARCHAR(300),
  CONSTRAINT fk_mov_contrato  FOREIGN KEY (id_contrato) REFERENCES contrato(id),
  CONSTRAINT fk_mov_turno     FOREIGN KEY (id_turno)    REFERENCES turnos(id_turno),
  CONSTRAINT fk_mov_usuario   FOREIGN KEY (id_usuario)  REFERENCES usuarios(id)
);
