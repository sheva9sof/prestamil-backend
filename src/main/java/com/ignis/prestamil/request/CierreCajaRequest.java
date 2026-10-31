package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/caja/cierre} (CAJ-06). Sin fecha se cierra el día de hoy del servidor; un día
 * anterior (pendiente) solo lo cierra Sistemas.
 */
@Getter
@Setter
public class CierreCajaRequest {
    private LocalDate fecha;
}
