package com.ignis.prestamil.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    // Campo canónico del incremento de avalúo (gate: usaAvaluoReal)
    private BigDecimal porcPrestamoSAvaluoReal;
    private Boolean cobrarReposicionContrato;
    private Boolean reposicionEsPorcentaje;
    private BigDecimal porcReposicion;
    private BigDecimal montoReposicion;
    @DecimalMin(value = "0.0000", message = "Los gastos operativos por venta no pueden ser negativos")
    @DecimalMax(value = "100.0000", message = "Los gastos operativos por venta no pueden exceder 100%")
    private BigDecimal comisionPorVentaPrenda;
    private Boolean aplicarSancionPorPeriodo;
    private BigDecimal porcSancionSemanal;
    /** RN-27: descuento sobre intereses. Solo el rol Sistemas puede modificar este campo. */
    private BigDecimal porcDescuentoInteres;
    private BigDecimal ley925;
    private BigDecimal ley725;
    private BigDecimal precioGramoPlata;
    private Integer diasGraciaSinInteres;
    private Integer diasAntesPaseVenta;
    private BigDecimal importeMinPrestamo;
}

