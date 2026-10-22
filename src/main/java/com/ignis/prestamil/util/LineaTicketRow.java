package com.ignis.prestamil.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Renglón etiqueta/valor del ticket de movimiento. Cada renglón se imprime por separado, así una
 * etiqueta larga que se parte en dos líneas no desalinea los importes de los renglones siguientes.
 */
@Getter
@AllArgsConstructor
public class LineaTicketRow {

    private final String etiqueta;

    private final String valor;
}
