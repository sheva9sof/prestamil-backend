package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;

/**
 * Parametros efectivos para el motor de calculo de contrato. Resueltos por
 * {@link CalculoContratoService#resolverParametros} usando el snapshot del contrato
 * si existe (contratos nuevos, changeset 026), o la configuracion vigente
 * (PlazoParametro + parametros_sistema id=8) como fallback documentado.
 *
 * @param porcInteres         porcentaje de interes por periodo
 * @param porcAlmacen         porcentaje de almacen por periodo
 * @param porcGastosAdmin     porcentaje de gastos administrativos; solo se imprime en el contrato
 *                            (clausula 11f y CAT), NO se cobra por periodo (GAP-09)
 * @param porcSancionSemanal  porcentaje de sancion por semana vencida (sobre el saldo capital)
 * @param diasGraciaSancion   dias de gracia (D.G.S.C. de COCAE, el mal-nombrado {@code dias_gracia_sin_interes}
 *                            de PlazoParametro): si se paga dentro de ellos no hay sancion (RPG); rebasados,
 *                            NO se descuentan del atraso (RN-05)
 * @param aplicarSancion      si {@code false}, la sancion siempre es 0 sin importar el atraso
 * @param porcIva             porcentaje de IVA a aplicar sobre (interes + almacen + sancion)
 */
public record ParametrosCalculo(
        BigDecimal porcInteres,
        BigDecimal porcAlmacen,
        BigDecimal porcGastosAdmin,
        BigDecimal porcSancionSemanal,
        int diasGraciaSancion,
        boolean aplicarSancion,
        BigDecimal porcIva
) {}
