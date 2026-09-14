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
 * @param porcGastosAdmin     porcentaje de gastos administrativos por periodo
 * @param porcSancionSemanal  porcentaje de sancion por semana vencida (sobre el prestamo)
 * @param diasGraciaSancion   dias de tolerancia antes de que aplique la sancion (equivale al mal-nombrado
 *                            {@code dias_gracia_sin_interes} de PlazoParametro; ver comentario ahi)
 * @param aplicarSancion      si {@code false}, la sancion siempre es 0 sin importar el atraso
 * @param porcIva             porcentaje de IVA a aplicar sobre (interes + sancion + almacen + gastos)
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
