package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PrecioOroRequest {

    @NotNull
    @Positive
    private BigDecimal precioGramoBase;

    /** Conservado por compatibilidad; el servicio usa siempre base 24K. */
    private Integer baseKilataje = 24;

    /** Conservado por compatibilidad; el servicio calcula siempre sobre PORCENTAJE. */
    private String calcularSobre = "PORCENTAJE";
}
