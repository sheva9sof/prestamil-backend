package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PlazoParametroRequest {
    
    @NotNull(message = "El ID del plazo es obligatorio")
    private Long plazoId;

    @NotNull(message = "El ID del tipo de prenda es obligatorio")
    private Integer tipoPrendaId;

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
}

