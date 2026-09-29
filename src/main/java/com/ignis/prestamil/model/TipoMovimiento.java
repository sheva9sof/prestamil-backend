package com.ignis.prestamil.model;

/**
 * Código corto del tipo de movimiento (sección 6 del plan de Finiquitos y Refrendos).
 * Se conservan los códigos de COCAE donde existen; el color vive en el frontend.
 */
public enum TipoMovimiento {
    EMP("Empeño"),
    RF("Refrendo"),
    RPG("Refrendo en periodo de gracia"),
    RC("Refrendo con abono a capital"),
    RP("Refrendo parcial"),
    RPX("Refrendo parcial extemporáneo"),
    RX("Refrendo extemporáneo"),
    FI("Finiquito"),
    FX("Finiquito extemporáneo"),
    RE("Reposición de contrato"),
    PV("Pase a venta"),
    PVA("Pase a venta anticipado"),
    RM("Pago de remanente");

    private final String etiqueta;

    TipoMovimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre para el ticket y los reportes; la API sigue serializando el código. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
