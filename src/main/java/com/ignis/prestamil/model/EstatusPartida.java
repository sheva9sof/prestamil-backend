package com.ignis.prestamil.model;

/**
 * Estatus de una partida (columna "St" de COCAE). Una sola partida VEN o APA bloquea
 * refrendo y finiquito de todo el contrato (RN-17).
 */
public enum EstatusPartida {
    /** En operación. */
    OP,
    /** Finiquitada (entregada al cliente). */
    FIN,
    /** Vendida. */
    VEN,
    /** Apartada. */
    APA,
    /** Cancelada: devuelta al cliente al cancelar el contrato recién creado (C-06, RN-26). */
    CAN
}
