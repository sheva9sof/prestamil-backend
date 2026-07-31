package com.ignis.prestamil.response;

import com.ignis.prestamil.model.TipoMovimiento;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class MovimientoResponse {
    private Long id;
    private Long idContrato;
    private String folioContrato;
    private TipoMovimiento tipo;
    private BigDecimal monto;
    private BigDecimal interes;
    private BigDecimal sancion;
    private Integer semanasVencidas;
    private LocalDateTime fecha;
    private String observaciones;
    private Integer numRefrendos;
    private LocalDateTime nuevaFechaVencimiento;
}
