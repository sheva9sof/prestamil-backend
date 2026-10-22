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
    // Valor de la prenda por gramo (oro del kilataje × factor de hechura), sin %Prestamo ni aumento.
    // Solo lo llena GET /api/plazos/{id}/alhajas (PlazoService.getTablaAlhajas).
    private BigDecimal precioAvaluo;
}
