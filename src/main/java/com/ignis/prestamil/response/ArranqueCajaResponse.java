package com.ignis.prestamil.response;

import com.ignis.prestamil.model.SerieFolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saldo de arranque de caja de la sucursal y folios de cada serie (CAJ-06), para la pantalla de Sistemas.
 *
 * @param fecha             día de arranque; {@code null} si la sucursal no tiene
 * @param saldoBoveda       saldo de la bóveda al abrir el día de arranque; {@code null} si no tiene
 * @param confirmado        Sistemas ya lo capturó: no cambia más
 * @param usuarioCaptura    quién lo capturó
 * @param fechaCaptura      cuándo se capturó
 * @param fechaUltimoCierre último día de caja cerrado
 * @param folios            último folio emitido de cada serie
 * @param hoy               fecha de hoy del servidor; el arranque no puede ser posterior
 * @param puedeCapturar     el rol del usuario puede capturarlo y todavía no está confirmado
 */
public record ArranqueCajaResponse(
        LocalDate fecha,
        BigDecimal saldoBoveda,
        boolean confirmado,
        String usuarioCaptura,
        LocalDateTime fechaCaptura,
        LocalDate fechaUltimoCierre,
        List<Folio> folios,
        LocalDate hoy,
        boolean puedeCapturar
) {

    /**
     * Último folio emitido de una serie de {@code FolioService}; el inicial capturado no puede ser menor.
     *
     * @param serie       serie del folio
     * @param ultimoFolio último folio emitido (0 si la serie no ha emitido ninguno)
     */
    public record Folio(SerieFolio serie, long ultimoFolio) {
    }
}
