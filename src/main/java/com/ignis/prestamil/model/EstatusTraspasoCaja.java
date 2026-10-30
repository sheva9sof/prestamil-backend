package com.ignis.prestamil.model;

/**
 * Estatus de un {@link TraspasoCaja}. Un traspaso nunca se borra: la cancelación lo marca.
 */
public enum EstatusTraspasoCaja {
    ACTIVO,
    CANCELADO
}
