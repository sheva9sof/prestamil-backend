--liquibase formatted sql

--changeset emm-a:044-1
--comment: D-11 / C-06: snapshot pre-contrato en EMPs existentes para que la cancelacion los reverta correctamente a saldo 0 y estatus CANCELADO. Antes de este changeset los EMPs se creaban sin saldo_anterior/estatus_anterior/num_refrendos_anterior, y al cancelar el contrato quedaba VIGENTE con el saldo intacto. Solo toca EMPs no cancelados sin snapshot: no reescribe nada previamente capturado.
UPDATE movimiento_contrato
SET saldo_anterior     = 0.00,
    estatus_anterior   = 'CANCELADO',
    num_refrendos_anterior = 0
WHERE tipo = 'EMP'
  AND cancelado = FALSE
  AND saldo_anterior IS NULL;

--changeset emm-a:044-2
--comment: D-11: diagnostico de contratos sin EMP (pre-F0). No inventamos movimientos ni salidas de caja historicas; solo registramos en bitacora cuales quedarian sin camino de cancelacion por la app. Si existen, el procedimiento es manual (Sistemas).
INSERT INTO bitacora (nombreUsuario, fecha, valorViejo, valorNuevo, tipoMov)
SELECT 'SISTEMA',
       NOW(),
       CONCAT('contrato=', c.folio, ' id=', c.id, ' fecha_apertura=', c.fecha_apertura),
       'Contrato sin movimiento EMP (pre-F0). La cancelacion por la app no esta disponible; el procedimiento es manual (Sistemas).',
       'DIAGNOSTICO_EMP_FALTANTE'
FROM contrato c
LEFT JOIN movimiento_contrato m
  ON m.id_contrato = c.id AND m.tipo = 'EMP'
WHERE m.id IS NULL;
