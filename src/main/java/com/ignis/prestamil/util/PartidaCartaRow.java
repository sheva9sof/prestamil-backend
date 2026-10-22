package com.ignis.prestamil.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Fila de partida de la carta responsiva. Jasper lee los campos por getter, por eso no es un record.
 */
@Getter
@AllArgsConstructor
public class PartidaCartaRow {

    /** Clave, descripción general y específica de la prenda. */
    private final String descripcion;

    /** Avalúo del contrato: "$18,000.00". */
    private final String avaluo;

    /** Monto del préstamo: "$9,000.00". */
    private final String prestamo;
}
