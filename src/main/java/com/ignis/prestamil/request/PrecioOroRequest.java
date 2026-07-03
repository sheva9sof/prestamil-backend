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

    /** Kilataje de referencia: 21 o 24 */
    private Integer baseKilataje = 24;

    /** PRESTAMO = input es precio de préstamo directo; PORCENTAJE = input es precio de avalúo */
    private String calcularSobre = "PRESTAMO";

    /** Factor de hechura Fundir (90 = 90%). Opcional: si es nulo conserva el vigente. */
    private BigDecimal factorFundir;

    /** Factor de hechura Normal (100 = 100%). Opcional: si es nulo conserva el vigente. */
    private BigDecimal factorNormal;

    /** Factor de hechura Especial (110 = 110%). Opcional: si es nulo conserva el vigente. */
    private BigDecimal factorEspecial;
}
