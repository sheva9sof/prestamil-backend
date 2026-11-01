package com.ignis.prestamil.response;

import java.time.LocalDate;

/**
 * Arranque de la pantalla del reporte mensual de corte de caja (REP-06): con qué meses arma el selector.
 *
 * @param hoy           fecha de hoy del servidor; no hay meses posteriores al de hoy
 * @param fechaArranque día de arranque de caja; antes de su mes el reporte sale sin cifras. {@code null} si
 *                      la sucursal todavía no tiene arranque
 */
public record CorteCajaMensualContextoResponse(LocalDate hoy, LocalDate fechaArranque) {
}
