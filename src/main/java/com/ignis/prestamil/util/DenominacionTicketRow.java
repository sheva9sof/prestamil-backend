package com.ignis.prestamil.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Renglón Cantidad/Denominación/Importe del ticket de traspaso entre cajas (CAJ-03). Los valores llegan
 * ya formateados.
 */
@Getter
@AllArgsConstructor
public class DenominacionTicketRow {

    private final String cantidad;

    private final String denominacion;

    private final String importe;
}
