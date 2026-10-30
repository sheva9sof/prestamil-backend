package com.ignis.prestamil.model;

/**
 * Tipo de caja, es decir, el lugar donde está el dinero (RC-01). Se guarda por nombre en {@code caja.tipo}.
 */
public enum TipoCaja {
    /** Cuenta del banco o tesorería; origen de las ministraciones y destino de las concentraciones. */
    CONCENTRADORA,
    /** Caja fuerte de la sucursal. */
    BOVEDA,
    /** Gaveta del cajero. Hoy hay una por sucursal (H-05). */
    VENTANILLA
}
