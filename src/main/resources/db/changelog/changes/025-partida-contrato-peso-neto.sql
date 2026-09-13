--liquibase formatted sql

--changeset emm-a:025-1
--comment: peso_gramos pasa a llamarse peso_neto: contiene solo el metal precioso y es el que alimenta la formula de prestamo. Confirmado con Jorge 2026-09-08.
ALTER TABLE partida_contrato
  CHANGE COLUMN peso_gramos peso_neto DECIMAL(10,4) NULL;
--rollback ALTER TABLE partida_contrato CHANGE COLUMN peso_neto peso_gramos DECIMAL(10,4) NULL;

--changeset emm-a:025-2
--comment: peso_total = peso fisico de la pieza completa (metal + piedras/plastico/soldadura). Informativo, NO entra en el calculo. NULL para Varios y Autos/Motos.
ALTER TABLE partida_contrato
  ADD COLUMN peso_total DECIMAL(10,4) NULL AFTER peso_neto;
--rollback ALTER TABLE partida_contrato DROP COLUMN peso_total;

--changeset emm-a:025-3
--comment: Las partidas ya capturadas se asumen 100% metal, asi que su peso total iguala al neto.
UPDATE partida_contrato SET peso_total = peso_neto WHERE peso_neto IS NOT NULL;
--rollback UPDATE partida_contrato SET peso_total = NULL;
