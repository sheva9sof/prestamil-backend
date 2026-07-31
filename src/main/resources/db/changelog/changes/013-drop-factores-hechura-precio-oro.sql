--liquibase formatted sql

--changeset emm-a:013-1
--comment: Eliminar factores de hechura (factor_fundir/normal/especial) de precio_oro.
--          Codigo muerto desde Phase 4: recalcularRegistros deriva precio_base de oro_tabla_prestamo,
--          no de estos factores (D-17). Se elimina para no mantener dos fuentes de precio.
ALTER TABLE precio_oro
  DROP COLUMN factor_fundir,
  DROP COLUMN factor_normal,
  DROP COLUMN factor_especial;
--rollback ALTER TABLE precio_oro ADD COLUMN factor_fundir DECIMAL(7,4) NOT NULL DEFAULT 90.0000 AFTER base_kilataje, ADD COLUMN factor_normal DECIMAL(7,4) NOT NULL DEFAULT 100.0000 AFTER factor_fundir, ADD COLUMN factor_especial DECIMAL(7,4) NOT NULL DEFAULT 110.0000 AFTER factor_normal;
