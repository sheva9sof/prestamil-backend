package com.ignis.prestamil.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultados de un pase de almoneda en una fecha (C-11). Lista vacia en cada pestaña cuando no
 * corrio el pase ese dia o cuando no hubo cambios de ese tipo. {@code fecha} es la que realmente
 * se consulto: para quien no puede elegir otra fecha (OTR-01) es la de hoy del servidor, aunque
 * haya pedido otra.
 */
public record ResultadosPaseAlmonedaResponse(
        LocalDate fecha,
        boolean puedeElegirFecha,
        List<CarteraVencidaRow> carteraVencida,
        List<PaseAVentaRow> paseAVenta
) {
}
