package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PartidaContratoResponse {
    private Long id;
    private Integer numPartida;
    private Integer idTipoPrenda;
    private String tipoPrendaNombre;
    private Integer idValorPrenda;
    private String clavePrenda;
    private String descripcion;
    private Integer cantidad;
    private BigDecimal pesoGramos;
    private Integer kilataje;
    private BigDecimal ley;
    private String hechura;
    private BigDecimal precioXGramo;
    private BigDecimal avaluoReal;
    private BigDecimal avaluoContrato;
    private BigDecimal montoPrestamo;
    private String subtipo;
    private String marca;
    private String modelo;
    private String serieImei;
    private String estadoFisico;
}
