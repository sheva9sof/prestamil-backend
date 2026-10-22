package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Resultado del recálculo server-side de una partida bajo un plazo específico (D-06).
 * El servidor calcula precio por gramo, avalúo real, préstamo máximo, préstamo final
 * (clamped al máximo y redondeado a múltiplos de $5 cuando corresponda) y avalúo de
 * contrato para que el cliente nunca "conserve" valores del plazo anterior.
 */
@Getter
@Setter
public class PartidaRecalculadaResponse {
    private Integer numPartida;
    private Integer idTipoPrenda;
    private BigDecimal precioXGramo;
    private BigDecimal avaluoReal;
    private BigDecimal prestamoMaximo;
    private BigDecimal montoPrestamo;
    private BigDecimal avaluoContrato;
}
