package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;

/**
 * Desglose completo de un cobro de contrato (refrendo o fila del PDF). Todos los montos
 * en escala 2. Base del IVA = interes + almacen + gastosAdmin + sancion. El abono de
 * capital NO lleva IVA y se suma por fuera por el llamador; por eso el motor no lo
 * recibe ni lo devuelve.
 *
 * @param prestamo         monto de prestamo del contrato (referencia)
 * @param interes          prestamo x porcInteres x periodoAcumulado / 100
 * @param almacen          prestamo x porcAlmacen x periodoAcumulado / 100
 * @param gastosAdmin      prestamo x porcGastosAdmin x periodoAcumulado / 100
 * @param sancion          ver {@link DesgloseSancion#monto}
 * @param semanasVencidas  semanas de sancion aplicables (0 si toggle apagado o dentro de gracia)
 * @param baseIva          interes + almacen + gastosAdmin + sancion
 * @param iva              baseIva x porcIva / 100, truncado DOWN a 2 decimales (regla COCAE)
 * @param total            baseIva + iva  (el llamador suma el abono de capital por fuera)
 */
public record DesgloseCobro(
        BigDecimal prestamo,
        BigDecimal interes,
        BigDecimal almacen,
        BigDecimal gastosAdmin,
        BigDecimal sancion,
        int semanasVencidas,
        BigDecimal baseIva,
        BigDecimal iva,
        BigDecimal total
) {}
