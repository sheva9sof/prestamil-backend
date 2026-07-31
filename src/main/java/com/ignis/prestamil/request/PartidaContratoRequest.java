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

    /** Ley de la plata (p. ej. 925 o 725). Solo aplica a prendas de plata. */
    private BigDecimal ley;

    private String hechura;
    private BigDecimal precioXGramo;

    @NotNull
    private BigDecimal avaluoReal;

    /**
     * Avalúo a imprimir en el contrato. Opcional: si el plazo usa avalúo real
     * el servidor lo recalcula y este valor se ignora.
     */
    private BigDecimal avaluoContrato;

    /**
     * Monto de préstamo solicitado. El servidor valida que no supere el máximo
     * calculado para el tipo de prenda; solo puede ajustarse hacia abajo.
     */
    @NotNull
    private BigDecimal montoPrestamo;

    private String subtipo;
    private String marca;
    private String modelo;
    private String serieImei;
    private String estadoFisico;
}
