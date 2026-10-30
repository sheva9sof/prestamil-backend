package com.ignis.prestamil.model;

/**
 * Tabla de donde sale un flujo de caja ({@code movimiento_caja.origen_tipo}). El id del registro va en
 * {@code origen_id} sin FK (A-02 de PLAN-CORTE-CAJA), así los changesets de Caja y de Ventas no dependen
 * entre sí. Se guarda por nombre: no renombrar un valor existente.
 */
public enum OrigenCaja {
    /** {@code movimiento_contrato.id}: empeño, cobros, reposición y sus cancelaciones. */
    MOVIMIENTO_CONTRATO,
    /** Id de la venta (módulo de Ventas). */
    VENTA,
    /** Id de la cancelación de venta (módulo de Ventas). */
    CANCELACION_VENTA
}
