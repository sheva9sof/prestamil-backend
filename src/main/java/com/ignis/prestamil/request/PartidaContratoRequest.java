package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PartidaContratoRequest {

    @NotNull
    private Integer idTipoPrenda;

    private Integer idValorPrenda;
    private String clavePrenda;

    @NotBlank
    private String descripcion;

    private Integer cantidad = 1;
    private BigDecimal pesoGramos;
    private Integer kilataje;
    private String hechura;
    private BigDecimal precioXGramo;

    @NotNull
    private BigDecimal avaluoReal;

    @NotNull
    private BigDecimal avaluoContrato;

    @NotNull
    private BigDecimal montoPrestamo;

    private String subtipo;
    private String marca;
    private String modelo;
    private String serieImei;
    private String estadoFisico;
}
