package com.ignis.prestamil.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Fila de partida del ticket de movimiento. Jasper lee los campos por getter, por eso no es un record.
 */
@Getter
@AllArgsConstructor
public class PartidaTicketRow {

    /** Número de partida. */
    private final String partida;

    /** Clave y descripción de la prenda. */
    private final String descripcion;

    /** Tipo, kilataje/hechura y peso: "AL · 14K / HE · 12.50 g". */
    private final String detalle;
}
