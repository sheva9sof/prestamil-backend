package com.ignis.prestamil.response;

import com.ignis.prestamil.model.TipoDenominacion;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DenominacionResponse {
    private Integer id;
    private BigDecimal valor;
    private TipoDenominacion tipo;
    private Integer orden;
    private Boolean activa;
}
