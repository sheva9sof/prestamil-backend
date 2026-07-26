--liquibase formatted sql

--changeset emm-a:017-1
--comment: Reinstaura factor_fundir/normal/especial en precio_oro. El usuario confirmo
--          que el factor de ajuste por hechura SI aplica en la operacion real de COCAE
--          (quick task 260726-lin) -- la conclusion de D-17/Phase 4.1 era correcta sobre
--          el codigo (no participaba en ningun calculo en ese momento) pero incompleta
--          sobre el negocio. Seed 100.0000 (neutro) intencional: al desplegar, ningun
--          monto vigente cambia; los valores reales por hechura los captura el usuario
--          desde la UI cuando tenga las capturas de COCAE.
ALTER TABLE precio_oro
  ADD COLUMN factor_fundir   DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER base_kilataje,
  ADD COLUMN factor_normal   DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_fundir,
  ADD COLUMN factor_especial DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_normal;
--rollback ALTER TABLE precio_oro DROP COLUMN factor_fundir, DROP COLUMN factor_normal, DROP COLUMN factor_especial;
