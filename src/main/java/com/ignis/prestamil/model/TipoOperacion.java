package com.ignis.prestamil.model;

/**
 * Operación que el cajero pide cotizar o registrar sobre un contrato. El motor decide el
 * {@link TipoMovimiento} resultante según la fecha: por ejemplo, REFRENDO puede quedar en
 * RF, RPG (periodo de gracia) o RX (extemporáneo).
 */
public enum TipoOperacion {
    REFRENDO,
    FINIQUITO,
    ABONO_CAPITAL,
    REFRENDO_PARCIAL
}
