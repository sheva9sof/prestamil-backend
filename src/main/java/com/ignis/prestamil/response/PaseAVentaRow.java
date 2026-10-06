package com.ignis.prestamil.response;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Fila de la pestaña "Pase a venta" (C-11). Lo que el gerente usa para sacar la prenda de boveda y
 * pasarla a piso de venta.
 */
@Builder
public record PaseAVentaRow(
        String folioContrato,
        Integer numPartida,
        String descripcion,
        LocalDate fechaPase
) {
}
