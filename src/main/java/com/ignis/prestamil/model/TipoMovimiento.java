package com.ignis.prestamil.model;

import java.util.EnumSet;
import java.util.Set;

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

    /**
     * Tipos que cuentan como "movimiento del día" para la regla RN-29 (C-01): si existe uno no
     * cancelado hoy en el contrato, ninguna operación de cobro queda disponible.
     *
     * <p>EMP cuenta: un contrato creado hoy no se refrenda ni abona hoy (TODO G-02). RE y PV no
     * cuentan: una reposición no bloquea los cobros (TODO G-01) y el pase a venta es automático.</p>
     */
    public static final Set<TipoMovimiento> CUENTAN_UNO_POR_DIA =
            EnumSet.of(EMP, RF, RPG, RC, RP, RPX, RX, FI, FX);
}
