package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PlazoParametroResponse {
    private Long plazoId;
    private Integer tipoPrendaId;
    private TipoPrendaResponse tipoPrenda;
    private BigDecimal porcInteres;
    private BigDecimal porcAlmacen;
    private BigDecimal porcGastosAdmin;
    private BigDecimal porcInteresTotal;
    private BigDecimal cat;
    private Integer numMaxRefrendos;
    private BigDecimal porcPrestamoSAvaluo;
    private Boolean usaAvaluoReal;
    private BigDecimal porcPrestamoSAvaluoReal;
    private Boolean cobrarReposicionContrato;
    private Boolean reposicionEsPorcentaje;
    private BigDecimal porcReposicion;
    private BigDecimal montoReposicion;
    private BigDecimal comisionPorVentaPrenda;
    private Boolean aplicarSancionPorPeriodo;
    private Integer diasGraciaSinInteres;
    private Integer diasAntesPaseVenta;
    private BigDecimal importeMinPrestamo;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
}

