package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Datos para registrar un refrendo (extemporáneo o no), abono o finiquito de un contrato.
 */
@Getter
@Setter
public class RefrendoRequest {

    @NotNull
    private Long idContrato;

    /** Abono a capital opcional (aplica en ABONO o finiquito parcial). */
    private BigDecimal abonoCapital;

    /** Observaciones libres para el movimiento. */
    private String observaciones;
}
