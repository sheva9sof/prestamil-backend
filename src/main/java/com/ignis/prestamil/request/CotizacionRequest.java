package com.ignis.prestamil.request;

import com.ignis.prestamil.model.TipoOperacion;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Datos para cotizar una operación sobre un contrato. La fecha de operación es siempre la del
 * servidor y el descuento sale de la parametrización (RN-27): el cliente no los envía.
 */
@Getter
@Setter
public class CotizacionRequest {

    @NotNull
    private Long contratoId;

    @NotNull
    private TipoOperacion tipoOperacion;

    /** Periodos a cubrir; obligatorio solo en REFRENDO_PARCIAL. */
    private Integer periodos;

    /** Abono a capital; solo en ABONO_CAPITAL. */
    private BigDecimal abonoCapital;
}
