package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PrecioOroResponse {
    private Integer id;
    private Integer sucursalId;
    private BigDecimal precioGramo24k;
    private String calcularSobre;
    private Integer baseKilataje;
    private BigDecimal factorFundir;
    private BigDecimal factorNormal;
    private BigDecimal factorEspecial;
    private LocalDateTime actualizadoEn;
    private String actualizadoPor;
}
