--liquibase formatted sql

--changeset emm-a:006-1
--comment: Agregar sucursal_id a plazo_parametro (default=1 para datos existentes)
ALTER TABLE plazo_parametro
  ADD COLUMN IF NOT EXISTS sucursal_id INT NOT NULL DEFAULT 1;

--changeset emm-a:006-1b
--comment: Eliminar FK fk_pp_plazo_categoria antes de modificar PK (previene error de integridad referencial)
ALTER TABLE plazo_parametro
  DROP FOREIGN KEY fk_pp_plazo_categoria;

--changeset emm-a:006-2
--comment: Reemplazar PK de plazo_parametro para incluir sucursal_id
ALTER TABLE plazo_parametro
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (plazo_id, tipo_prenda_id, sucursal_id);

--changeset emm-a:006-2c
--comment: Recrear FK fk_pp_plazo_categoria apuntando solo a plazo_prenda(plazo_id, tipo_prenda_id)
ALTER TABLE plazo_parametro
  ADD CONSTRAINT fk_pp_plazo_categoria FOREIGN KEY (plazo_id, tipo_prenda_id) REFERENCES plazo_prenda (plazo_id, tipo_prenda_id);

--changeset emm-a:006-3
--comment: FK plazo_parametro.sucursal_id -> sucursal.id
ALTER TABLE plazo_parametro
  ADD CONSTRAINT fk_pp_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id);

--changeset emm-a:006-4
--comment: Agregar sucursal_id a plazo_hechura_alhaja
ALTER TABLE plazo_hechura_alhaja
  ADD COLUMN IF NOT EXISTS sucursal_id INT NOT NULL DEFAULT 1;

--changeset emm-a:006-5
--comment: FK plazo_hechura_alhaja.sucursal_id -> sucursal.id
ALTER TABLE plazo_hechura_alhaja
  ADD CONSTRAINT fk_pha_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id);

--changeset emm-a:006-6
--comment: Indice compuesto para busquedas por sucursal+plazo
CREATE INDEX IF NOT EXISTS idx_pha_sucursal ON plazo_hechura_alhaja(sucursal_id, id_plazo);
