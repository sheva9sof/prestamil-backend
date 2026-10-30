package com.ignis.prestamil.model;

/**
 * Los cuatro traspasos de dinero entre cajas (RC-02). Se guarda por nombre en {@code traspaso_caja.tipo}.
 * El tipo fija la caja de origen y la de destino; el usuario no las elige.
 */
public enum TipoTraspasoCaja {
    /** Tesorería → bóveda. */
    MINISTRACION(TipoCaja.CONCENTRADORA, TipoCaja.BOVEDA),
    /** Bóveda → banco. */
    CONCENTRACION(TipoCaja.BOVEDA, TipoCaja.CONCENTRADORA),
    /** Bóveda → ventanilla. */
    DOTACION(TipoCaja.BOVEDA, TipoCaja.VENTANILLA),
    /** Ventanilla → bóveda. */
    RECOLECCION(TipoCaja.VENTANILLA, TipoCaja.BOVEDA);

    private final TipoCaja origen;
    private final TipoCaja destino;

    TipoTraspasoCaja(TipoCaja origen, TipoCaja destino) {
        this.origen = origen;
        this.destino = destino;
    }

    /** Tipo de la caja de donde sale el dinero. */
    public TipoCaja origen() {
        return origen;
    }

    /** Tipo de la caja a donde llega el dinero. */
    public TipoCaja destino() {
        return destino;
    }
}
