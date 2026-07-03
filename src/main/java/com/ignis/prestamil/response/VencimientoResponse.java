package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de la tabla de amortización (referencia). Se calcula al vuelo y no se persiste.
 */
@Getter
@Setter
public class VencimientoResponse {
    private Integer periodo;
    private LocalDate fecha;
    private BigDecimal interes;
    private BigDecimal total;
}
