package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PlazoHechuraAlhajaResponse {
    private Integer idPlazo;
    private Integer sucursalId;
    private Integer kilataje;
    private String hechura;
    private String hechuraDescripcion; // "Fina" | "Normal" | "Especial"
    private BigDecimal precioBase;
    private BigDecimal porcAumento;
    private BigDecimal precioPrestamo;
}
