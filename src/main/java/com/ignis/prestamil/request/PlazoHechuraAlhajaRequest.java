package com.ignis.prestamil.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PlazoHechuraAlhajaRequest {

    @NotNull(message = "kilataje es requerido")
    private Integer kilataje;

    @NotBlank(message = "hechura es requerida")
    @Pattern(regexp = "[FNE]", message = "hechura debe ser F, N o E")
    private String hechura;

    @NotNull(message = "precioBase es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "precioBase debe ser mayor o igual a 0")
    private BigDecimal precioBase;

    @NotNull(message = "porcAumento es requerido")
    @DecimalMin(value = "0.0", inclusive = true, message = "porcAumento debe ser mayor o igual a 0")
    private BigDecimal porcAumento;
}
