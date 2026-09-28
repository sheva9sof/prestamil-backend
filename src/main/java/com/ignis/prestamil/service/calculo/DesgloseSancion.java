package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;

/**
 * Resultado del calculo de sancion por extemporaneidad, con los intermedios expuestos
 * para trazabilidad y tests.
 *
 * @param diasAtraso        dias desde el vencimiento hasta el pago; minimo 0
 * @param semanasVencidas   semanas de sancion: {@code Math.ceil(diasAtraso/7.0)} si el atraso rebasa la
 *                          gracia (la gracia no se descuenta, RN-05); 0 si se paga dentro de la gracia o
 *                          el toggle {@code aplicarSancion} esta apagado
 * @param monto             monto de sancion = base x porcSancionSemanal/100 x semanasVencidas
 *                          (escala 2, HALF_UP)
 */
public record DesgloseSancion(
        int diasAtraso,
        int semanasVencidas,
        BigDecimal monto
) {}
