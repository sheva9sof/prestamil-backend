package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Renglón del desglose de un traspaso (RC-03).
 */
@Getter
@Setter
public class TraspasoCajaDenominacionResponse {
    private Integer denominacionId;
    private BigDecimal valor;
    private Integer cantidad;
    private BigDecimal importe;
}
