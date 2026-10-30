package com.ignis.prestamil.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Concepto estructurado de un flujo de caja (CAJ-01b, H-02). Dice a qué renglón del corte va cada
 * registro de {@code movimiento_caja} y si es ENTRADA o SALIDA de la gaveta. Se guarda por nombre en
 * {@code movimiento_caja.concepto_caja}: no renombrar un valor existente.
 *
 * <p>Todo concepto tiene renglón: un concepto sin renglón rompe {@code ConceptoCajaTest}, así nada cae
 * en "otros" sin que nadie se entere.</p>
 */
public enum ConceptoCaja {
    /** Préstamo entregado al cliente al crear el contrato (EMP). */
    EMPENO(TipoMovimientoCaja.SALIDA, RenglonCorte.EMPENOS),
    /** El cliente devuelve el préstamo al cancelar un contrato del día (H-03). */
    CANCELACION_EMPENO(TipoMovimientoCaja.ENTRADA, RenglonCorte.CANCELACION_EMPENOS),
    /** Se devuelve al cliente lo cobrado al cancelar un refrendo, finiquito o abono (H-03). */
    DEVOLUCION_COBRO(TipoMovimientoCaja.SALIDA, RenglonCorte.DEVOLUCIONES),
    /** Pago del remanente de la venta al cliente (RM). TODO Q-C2: confirmar con Jorge que va en DEMASÍAS. */
    REMANENTE(TipoMovimientoCaja.SALIDA, RenglonCorte.DEMASIAS),
    /** RP y RPX. TODO Q-C3: confirmar con Jorge que el parcial va en ABONOS PARCIALES. */
    REFRENDO_PARCIAL(TipoMovimientoCaja.ENTRADA, RenglonCorte.ABONOS_PARCIALES),
    /** RC. */
    ABONO_CAPITAL(TipoMovimientoCaja.ENTRADA, RenglonCorte.ABONOS_CAPITAL),
    /** FI. */
    FINIQUITO(TipoMovimientoCaja.ENTRADA, RenglonCorte.FINIQUITOS),
    /** FX. */
    FINIQUITO_EXTEMPORANEO(TipoMovimientoCaja.ENTRADA, RenglonCorte.FINIQUITOS_EXTEMPORANEOS),
    /** RF y RPG. */
    REFRENDO(TipoMovimientoCaja.ENTRADA, RenglonCorte.REFRENDOS),
    /** RX. */
    REFRENDO_EXTEMPORANEO(TipoMovimientoCaja.ENTRADA, RenglonCorte.REFRENDOS_EXTEMPORANEOS),
    /** RE cobrada (H-04). La exenta ($0) no mueve caja. */
    REPOSICION(TipoMovimientoCaja.ENTRADA, RenglonCorte.REPOSICIONES),
    /** Venta de mostrador (módulo de Ventas, A-03). */
    VENTA_VITRINA(TipoMovimientoCaja.ENTRADA, RenglonCorte.VENTAS),
    /** Venta a fundición (módulo de Ventas, A-03). */
    VENTA_FUNDICION(TipoMovimientoCaja.ENTRADA, RenglonCorte.VENTAS),
    /** Devolución al cliente al cancelar una venta (módulo de Ventas, A-03, RV-15). */
    CANCELACION_VENTA(TipoMovimientoCaja.SALIDA, RenglonCorte.CANCELACIONES_VENTA);

    /**
     * Tipos de movimiento de contrato que nunca mueven caja: el pase a venta solo cambia el estatus
     * del contrato (importe 0), ni al registrarse ni al cancelarse.
     */
    public static final Set<TipoMovimiento> TIPOS_SIN_CAJA = EnumSet.of(TipoMovimiento.PV, TipoMovimiento.PVA);

    private final TipoMovimientoCaja tipo;
    private final RenglonCorte renglonCorte;

    ConceptoCaja(TipoMovimientoCaja tipo, RenglonCorte renglonCorte) {
        this.tipo = tipo;
        this.renglonCorte = renglonCorte;
    }

    /** ENTRADA o SALIDA de la gaveta; {@code MovimientoCajaService} la toma de aquí. */
    public TipoMovimientoCaja tipo() {
        return tipo;
    }

    /** Renglón del corte de caja donde se suma este concepto (sección 3 de PLAN-CORTE-CAJA). */
    public RenglonCorte renglonCorte() {
        return renglonCorte;
    }

    /**
     * Concepto del flujo de caja que genera un movimiento de contrato al registrarse.
     *
     * @param tipo tipo del movimiento de contrato
     * @return concepto de caja del movimiento
     * @throws IllegalArgumentException si el tipo está en {@link #TIPOS_SIN_CAJA}
     */
    public static ConceptoCaja deMovimiento(TipoMovimiento tipo) {
        // Switch exhaustivo: un TipoMovimiento nuevo no compila hasta que se decida su concepto
        return switch (tipo) {
            case EMP -> EMPENO;
            case RF, RPG -> REFRENDO;
            case RX -> REFRENDO_EXTEMPORANEO;
            case RP, RPX -> REFRENDO_PARCIAL;
            case RC -> ABONO_CAPITAL;
            case FI -> FINIQUITO;
            case FX -> FINIQUITO_EXTEMPORANEO;
            case RE -> REPOSICION;
            case RM -> REMANENTE;
            case PV, PVA -> throw new IllegalArgumentException("El movimiento " + tipo + " no mueve caja");
        };
    }

    /**
     * Concepto del flujo de caja que genera la cancelación de un movimiento de contrato: la del EMP
     * regresa el préstamo a la gaveta y la de un cobro devuelve al cliente lo cobrado.
     *
     * @param tipo tipo del movimiento de contrato que se cancela
     * @return concepto de caja de la cancelación
     * @throws IllegalArgumentException si el tipo no se cancela con flujo de caja: RE no se cancela
     *                                  (C-03), RM todavía no tiene flujo y los de {@link #TIPOS_SIN_CAJA}
     *                                  no mueven caja
     */
    public static ConceptoCaja deCancelacion(TipoMovimiento tipo) {
        return switch (tipo) {
            case EMP -> CANCELACION_EMPENO;
            case RF, RPG, RX, RP, RPX, RC, FI, FX -> DEVOLUCION_COBRO;
            case RE, RM, PV, PVA -> throw new IllegalArgumentException(
                    "La cancelación del movimiento " + tipo + " no tiene concepto de caja");
        };
    }
}
