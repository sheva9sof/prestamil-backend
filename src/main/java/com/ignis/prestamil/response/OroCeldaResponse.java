package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Representa una celda de la tabla de 24 celdas de %Prestamo (8 kilates x 3 hechuras)
 * con sus precios calculados para la pantalla de Configuración del Oro.
 */
@Getter
@Setter
public class OroCeldaResponse {

    private Integer kilataje;
    private String hechura;
    private String hechuraDescripcion;
    private BigDecimal precioAvaluo;
    private BigDecimal porcPrestamo;
    private BigDecimal precioPrestamo;
    private boolean editable;
}
