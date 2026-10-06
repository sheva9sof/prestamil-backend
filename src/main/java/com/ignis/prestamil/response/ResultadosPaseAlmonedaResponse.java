package com.ignis.prestamil.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultados de un pase de almoneda en una fecha (C-11). Lista vacia en cada pestaña cuando no
 * corrio el pase ese dia o cuando no hubo cambios de ese tipo.
 */
public record ResultadosPaseAlmonedaResponse(
        LocalDate fecha,
        List<CarteraVencidaRow> carteraVencida,
        List<PaseAVentaRow> paseAVenta
) {
}
