package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;

/**
 * Resultado del calculo de sancion por extemporaneidad, con los intermedios expuestos
 * para trazabilidad y tests.
 *
 * @param diasAtraso        dias efectivos de atraso (fecha_pago - fecha_vencimiento - dias_gracia); minimo 0
 * @param semanasVencidas   semanas de sancion aplicables ({@code Math.ceil(diasAtraso/7.0)}); 0 si el
 *                          toggle {@code aplicarSancion} esta apagado
 * @param monto             monto de sancion = prestamo x porcSancionSemanal/100 x semanasVencidas
 *                          (escala 2, HALF_UP)
 */
public record DesgloseSancion(
        int diasAtraso,
        int semanasVencidas,
        BigDecimal monto
) {}
