package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;

/**
 * Desglose de un cobro de contrato (refrendo, fila del PDF o cotización). Todos los montos
 * en escala 2. Base del IVA = interes + almacen + sancion. Los gastos de administración
 * ("G.Oper. x Vta.") NO se cobran por periodo (GAP-09). El abono y el capital NO llevan IVA
 * y se suman por fuera; por eso el motor no los incluye aquí.
 *
 * @param base             base de cálculo: saldo capital en la cotización (RN-09), préstamo en el
 *                         contrato impreso y la amortización
 * @param interes          base x porcInteres x periodos / 100
 * @param almacen          base x porcAlmacen x periodos / 100
 * @param sancion          base x porcSancionSemanal / 100 x semanasVencidas
 * @param semanasVencidas  semanas de sancion cobradas (0 si el toggle esta apagado o se paga en gracia)
 * @param baseIva          interes + almacen + sancion
 * @param iva              baseIva x porcIva / 100, truncado DOWN a 2 decimales (regla COCAE)
 * @param total            baseIva + iva (el llamador suma abono o capital por fuera)
 */
public record DesgloseCobro(
        BigDecimal base,
        BigDecimal interes,
        BigDecimal almacen,
        BigDecimal sancion,
        int semanasVencidas,
        BigDecimal baseIva,
        BigDecimal iva,
        BigDecimal total
) {

    /** Interés + almacenaje: lo que COCAE muestra como "Intereses" ("Int x Per." × periodos). */
    public BigDecimal interesTotal() {
        return interes.add(almacen);
    }
}
