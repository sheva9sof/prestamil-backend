package com.ignis.prestamil.model;

/**
 * Tipo de cambio registrado en el detalle del pase de almoneda (C-11).
 *
 * <ul>
 *   <li>{@link #VENCIDO}: el contrato paso de VIGENTE a VENCIDO (cartera vencida).</li>
 *   <li>{@link #EN_VENTA}: la partida paso a EN_VENTA tras llegar su fecha de comercializacion.</li>
 * </ul>
 */
public enum TipoCambioPase {
    VENCIDO,
    EN_VENTA
}
