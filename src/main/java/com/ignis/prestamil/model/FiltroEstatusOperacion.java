package com.ignis.prestamil.model;

/**
 * Radios de estatus de la pantalla de Finiquitos y Refrendos (F2). Periodo de gracia, vencidos y en
 * venta se resuelven con las fechas del contrato, no solo con la columna estatus, para que sean
 * correctos aunque el pase diario no haya corrido.
 */
public enum FiltroEstatusOperacion {
    TODOS,
    /** Vigentes: hoy ≤ vencimiento. */
    EN_OPERACION,
    /** Con al menos un refrendo y sin cerrar. */
    REFRENDADOS,
    PERIODO_GRACIA,
    VENCIDOS,
    EN_VENTA,
    /** Con alguna partida vendida o apartada (RN-17). */
    VENDIDOS,
    FINIQUITADOS,
    CANCELADOS
}
