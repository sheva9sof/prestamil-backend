package com.ignis.prestamil.response;

import com.ignis.prestamil.service.calculo.CorteCajaResultado;

import java.time.LocalDate;

/**
 * Corte de caja para la pantalla (CAJ-07a) con el estado de su cierre (CAJ-06). {@code fecha} es la que
 * realmente se consultó: para quien no puede elegir otra (el cajero) es la de hoy del servidor, aunque haya
 * pedido otra, igual que OTR-01.
 *
 * @param fecha            día del corte mostrado
 * @param hoy              fecha de hoy del servidor; el arqueo de la gaveta solo tiene sentido para hoy
 * @param puedeElegirFecha si el usuario puede consultar el corte de otro día
 * @param corte            el corte tal como lo calcula {@code CorteCajaService} (congelado si el día está
 *                         cerrado); si no tiene cifras, trae el aviso y los importes en {@code null}
 * @param cierre           el cierre del día; {@code null} si sigue abierto o si lo congeló el cierre de un
 *                         día posterior (un día sin movimientos no se cierra por sí solo)
 * @param cerrado          el día ya no admite operaciones: la pantalla lo muestra en modo lectura
 * @param diaPendiente     el día ya pasó, tuvo movimientos y nadie lo cerró: solo Sistemas puede cerrarlo
 * @param puedeCerrar      el rol del usuario puede cerrar este día (el gerente hoy; Sistemas también un día
 *                         pendiente) y el día no está cerrado
 * @param avisoCierre      si {@code puedeCerrar}, lo que hoy impide cerrarlo (un turno abierto, un día
 *                         anterior pendiente, el arranque sin capturar); {@code null} si se puede cerrar
 * @param primerDiaPendiente   el día pendiente de cierre más antiguo de la sucursal, aunque se esté viendo
 *                             otro día; {@code null} si no hay
 * @param puedeCerrarPendiente el usuario puede cerrar días pendientes (Sistemas): la pantalla le ofrece ir a él
 */
public record CorteCajaResponse(
        LocalDate fecha,
        LocalDate hoy,
        boolean puedeElegirFecha,
        CorteCajaResultado corte,
        CierreCajaResponse cierre,
        boolean cerrado,
        boolean diaPendiente,
        boolean puedeCerrar,
        String avisoCierre,
        LocalDate primerDiaPendiente,
        boolean puedeCerrarPendiente
) {
}
