package com.ignis.prestamil.response;

import com.ignis.prestamil.service.calculo.CorteCajaResultado;

import java.time.LocalDate;

/**
 * Corte de caja para la pantalla (CAJ-07a). {@code fecha} es la que realmente se consultó: para quien no
 * puede elegir otra (el cajero) es la de hoy del servidor, aunque haya pedido otra, igual que OTR-01.
 *
 * @param fecha            día del corte mostrado
 * @param hoy              fecha de hoy del servidor; el arqueo de la gaveta solo tiene sentido para hoy
 * @param puedeElegirFecha si el usuario puede consultar el corte de otro día
 * @param corte            el corte tal como lo calcula {@code CorteCajaService}; si no tiene cifras, trae
 *                         el aviso y los importes en {@code null}
 */
public record CorteCajaResponse(
        LocalDate fecha,
        LocalDate hoy,
        boolean puedeElegirFecha,
        CorteCajaResultado corte
) {
}
