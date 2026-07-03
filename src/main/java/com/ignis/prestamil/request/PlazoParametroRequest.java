package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PlazoParametroRequest {

    private BigDecimal porcInteres;
    private BigDecimal porcAlmacen;
    private BigDecimal porcGastosAdmin;
    private BigDecimal porcInteresTotal;
    private BigDecimal cat;
    private Integer numMaxRefrendos;
    private BigDecimal porcPrestamoSAvaluo;
    private Boolean usaAvaluoReal;
    private BigDecimal porcIncrementoAvaluo;
    private Boolean cobrarReposicionContrato;
    private Boolean reposicionEsPorcentaje;
    private BigDecimal porcReposicion;
    private BigDecimal montoReposicion;
    private BigDecimal comisionPorVentaPrenda;
    private Boolean aplicarSancionPorPeriodo;
    private BigDecimal porcSancionSemanal;
    private BigDecimal ley925;
    private BigDecimal ley725;
    private BigDecimal precioGramoPlata;
    private Integer diasGraciaSinInteres;
    private Integer diasAntesPaseVenta;
    private BigDecimal importeMinPrestamo;
}

