package com.ignis.prestamil.model;

/**
 * Dirección del flujo de efectivo en caja (C-07). Base del corte de caja futuro.
 */
public enum TipoMovimientoCaja {
    /** Entra efectivo a la caja (cobro, devolución del cliente al cancelar un contrato recién creado). */
    ENTRADA,
    /** Sale efectivo de la caja (devolución al cliente, salida administrativa). */
    SALIDA
}
