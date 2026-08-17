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
    private BigDecimal interes;       // interés acumulado a este periodo
    private BigDecimal almacen;       // almacenaje/custodia acumulado
    private BigDecimal gastosAdmin;   // gastos administrativos acumulados
    private BigDecimal totalInteres;  // interés + almacén + gastos (acumulado)
    private BigDecimal iva;           // IVA sobre el total interés (16%, truncado — regla COCAE)
    private BigDecimal desempeno;     // préstamo + totalInteres + IVA (monto para desempeñar)
    private BigDecimal total;         // = desempeño (compat retro)
}
