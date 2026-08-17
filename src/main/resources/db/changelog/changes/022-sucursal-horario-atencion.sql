--liquibase formatted sql

--changeset emm-a:022-1
--comment: Horario de atención de la sucursal (texto libre) para imprimir en el contrato
ALTER TABLE sucursal
  ADD COLUMN horario_atencion VARCHAR(200) AFTER domingo;
