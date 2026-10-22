--liquibase formatted sql

-- ============================================================================
-- D-14 (plan 07-oct) - Correcciones de datos para registros con sancion mal
-- configurada. NO ESTA EN db.changelog-master.xml a proposito: aplicar solo
-- despues de aprobar el diagnostico contra la BD de produccion.
--
-- Diagnostico en dev (CasaEmp_DEV, 2026-10-08):
--   - plazo_parametro: 3 filas, todas con porc_sancion_semanal=2.0000 y
--     aplicar_sancion_por_periodo=1. No requiere correccion en dev.
--   - contrato id=9 (CTR-000009, VIGENTE): snap_porc_sancion_semanal=2.0000
--     pero snap_aplicar_sancion_periodo=0. El cobro ya aplica 2% (gate removido
--     del motor), pero el P_RESUMEN_MORATORIOS del PDF todavia respeta el gate
--     -> imprime 0% mientras caja cobra 2%. Divergencia PROFECO.
--   - contratos id=1..8 (pre-SANC-04): snap_porc_sancion_semanal=NULL y
--     snap_aplicar_sancion_periodo=NULL. resolverParametros cae a config
--     vigente por-campo (2.00%, true). No se tocan por default: es una
--     decision de negocio si quieren "congelar" ahora el 2% en el snapshot.
--
-- Si al correrse en produccion el diagnostico arroja mas filas afectadas,
-- ampliar/clonar este changeset con los IDs reales antes de aplicar.
-- ============================================================================

--changeset emm-a:040-1 runOnChange:false runAlways:false
--comment: D-14 - Alinear snap_aplicar_sancion_periodo=1 en contratos que tienen un porcentaje de sancion valido pero el toggle apagado (contratos pre-fix donde PDF imprimia 0 mientras caja cobraba el %). Preserva el porcentaje; solo prende el flag para que P_RESUMEN_MORATORIOS del jrxml salga consistente con el cobro real.
UPDATE `contrato`
SET `snap_aplicar_sancion_periodo` = 1
WHERE `snap_aplicar_sancion_periodo` = 0
  AND `snap_porc_sancion_semanal` IS NOT NULL
  AND `snap_porc_sancion_semanal` > 0;
--rollback UPDATE `contrato` SET `snap_aplicar_sancion_periodo` = 0 WHERE `id` IN (/* lista exacta a confirmar antes del rollback */);

--changeset emm-a:040-2 runOnChange:false runAlways:false
--comment: D-14 - plazo_parametro con porc_sancion_semanal = 0 o NULL se corrige al default (parametros_sistema id=19) para que la UI y PDF no muestren 0% silenciosamente ahora que el gate aplicarSancion ya no se consulta. En dev no hay filas afectadas; aplica solo si produccion las trae.
UPDATE `plazo_parametro` pp
JOIN `parametros_sistema` ps ON ps.id = 19
SET pp.`porc_sancion_semanal` = ps.`valor_numerico`
WHERE pp.`porc_sancion_semanal` IS NULL
   OR pp.`porc_sancion_semanal` = 0;
--rollback UPDATE `plazo_parametro` SET `porc_sancion_semanal` = 0 WHERE /* lista exacta a confirmar antes del rollback */;

--changeset emm-a:040-3 runOnChange:false runAlways:false
--comment: D-14 - plazo_parametro con aplicar_sancion_por_periodo = false o NULL. SANC-04 (2026-09-12) forzaba el flag a true al guardar; esta correccion limpia las filas que nunca se volvieron a guardar. En dev no hay filas afectadas.
UPDATE `plazo_parametro`
SET `aplicar_sancion_por_periodo` = 1
WHERE `aplicar_sancion_por_periodo` = 0
   OR `aplicar_sancion_por_periodo` IS NULL;
--rollback UPDATE `plazo_parametro` SET `aplicar_sancion_por_periodo` = 0 WHERE /* lista exacta a confirmar antes del rollback */;
