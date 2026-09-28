package com.ignis.prestamil.model;

/**
 * Código corto del tipo de movimiento (sección 6 del plan de Finiquitos y Refrendos).
 * Se conservan los códigos de COCAE donde existen; la etiqueta y el color viven en el frontend.
 */
public enum TipoMovimiento {
    /** Empeño (periodo 0). */
    EMP,
    /** Refrendo. */
    RF,
    /** Refrendo en periodo de gracia. */
    RPG,
    /** Refrendo con abono a capital. */
    RC,
    /** Refrendo parcial. */
    RP,
    /** Refrendo parcial extemporáneo. */
    RPX,
    /** Refrendo extemporáneo. */
    RX,
    /** Finiquito. */
    FI,
    /** Finiquito extemporáneo. */
    FX,
    /** Reposición / reimpresión de contrato. */
    RE,
    /** Pase a venta (automático). */
    PV,
    /** Pase a venta anticipado. */
    PVA,
    /** Pago de remanente. */
    RM
}
