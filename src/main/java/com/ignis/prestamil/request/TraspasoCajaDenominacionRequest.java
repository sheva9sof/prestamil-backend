package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Renglón del desglose de un traspaso: cuántas piezas de una denominación (RC-03). El importe del
 * renglón lo calcula el servidor.
 */
@Getter
@Setter
public class TraspasoCajaDenominacionRequest {

    @NotNull(message = "La denominación es obligatoria")
    private Integer denominacionId;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a cero")
    private Integer cantidad;
}
