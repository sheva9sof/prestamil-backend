package com.ignis.prestamil.model;

/**
 * Estatus del contrato para operar en caja, derivado de las fechas, del estatus persistido y del
 * estatus de las partidas (RN-16, RN-17). No se persiste: EN_GRACIA y APARTADO solo existen aquí.
 * Lo resuelve {@link com.ignis.prestamil.service.calculo.EstatusContratoResolver}.
 */
public enum EstatusOperativo {
    /** Hoy ≤ vencimiento. */
    VIGENTE,
    /** Vencimiento < hoy ≤ vencimiento + días de gracia. */
    EN_GRACIA,
    /** Gracia rebasada y antes de la fecha de comercialización. */
    VENCIDO,
    /** Desde la fecha de comercialización, sin prendas vendidas ni apartadas. */
    EN_VENTA,
    /** Al menos una partida apartada. */
    APARTADO,
    /** Al menos una partida vendida, o el contrato marcado como vendido. */
    VENDIDO,
    FINIQUITADO,
    CANCELADO
}
