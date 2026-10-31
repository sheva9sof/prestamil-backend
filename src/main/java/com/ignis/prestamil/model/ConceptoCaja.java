package com.ignis.prestamil.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Concepto estructurado de un flujo de caja (CAJ-01b, H-02). Dice a qué renglón del corte va cada
 * registro de {@code movimiento_caja} y si es ENTRADA o SALIDA de la gaveta. Se guarda por nombre en
 * {@code movimiento_caja.concepto_caja}: no renombrar un valor existente.
 *
 * <p>Hay tres clases de concepto (CAJ-01d, respuestas de Jorge del 2026-10-30):</p>
 * <ul>
 *   <li><b>De renglón fijo</b>: todo el importe va a {@link #renglonCorte()}.</li>
 *   <li><b>{@link #ABONO_CAPITAL}</b> (RC): se divide al calcular el corte. El capital va a ABONOS A
 *       CAPITAL y el interés, la sanción y el IVA a REFRENDOS o REFRENDOS EXTEMPORÁNEOS (Q-C3).</li>
 *   <li><b>Cancelaciones</b> ({@link #esCancelacion()}): no tienen renglón propio (Q-C1). Restan del
 *       renglón de la operación original, que se encuentra con {@code origen_tipo}/{@code origen_id}.</li>
 * </ul>
 * <p>La resolución completa, con la división de RC y el signo de las cancelaciones, está en
 * {@code ResolutorRenglonCorte}. {@code ConceptoCajaTest} falla si un concepto o un tipo de movimiento
 * queda sin renglón.</p>
 */
public enum ConceptoCaja {
    /** Préstamo entregado al cliente al crear el contrato (EMP). */
    EMPENO(TipoMovimientoCaja.SALIDA, RenglonCorte.EMPENOS),
    /** El cliente devuelve el préstamo al cancelar un contrato del día (H-03). Resta de EMPEÑOS. */
    CANCELACION_EMPENO(TipoMovimientoCaja.ENTRADA, null),
    /**
     * Se devuelve al cliente lo cobrado al cancelar un refrendo, finiquito o abono (H-03). Resta del
     * renglón del cobro original.
     */
    DEVOLUCION_COBRO(TipoMovimientoCaja.SALIDA, null),
    /** Pago del remanente de la venta al cliente (RM, Q-C2). */
    REMANENTE(TipoMovimientoCaja.SALIDA, RenglonCorte.DEMASIAS),
    /**
     * RC. El renglón es el de la parte de capital; la de interés, sanción e IVA se separa al calcular
     * el corte (regla 2 de la sección 3).
     */
    ABONO_CAPITAL(TipoMovimientoCaja.ENTRADA, RenglonCorte.ABONOS_CAPITAL),
    /** FI. */
    FINIQUITO(TipoMovimientoCaja.ENTRADA, RenglonCorte.FINIQUITOS),
    /** FX. */
    FINIQUITO_EXTEMPORANEO(TipoMovimientoCaja.ENTRADA, RenglonCorte.FINIQUITOS_EXTEMPORANEOS),
    /** RF, RPG y RP (Q-C3). */
    REFRENDO(TipoMovimientoCaja.ENTRADA, RenglonCorte.REFRENDOS),
    /** RX y RPX (Q-C3). */
    REFRENDO_EXTEMPORANEO(TipoMovimientoCaja.ENTRADA, RenglonCorte.REFRENDOS_EXTEMPORANEOS),
    /** RE cobrada (H-04, Q-C4). La exenta ($0) no mueve caja. */
    REPOSICION(TipoMovimientoCaja.ENTRADA, RenglonCorte.REPOSICIONES),
    /** Venta de mostrador (módulo de Ventas, A-03). */
    VENTA_VITRINA(TipoMovimientoCaja.ENTRADA, RenglonCorte.VENTAS),
    /** Venta a fundición (módulo de Ventas, A-03). */
    VENTA_FUNDICION(TipoMovimientoCaja.ENTRADA, RenglonCorte.VENTAS),
    /** Devolución al cliente al cancelar una venta (módulo de Ventas, A-03, A-08). Resta de VENTAS. */
    CANCELACION_VENTA(TipoMovimientoCaja.SALIDA, null);

    /**
     * Tipos de movimiento de contrato que nunca mueven caja: el pase a venta solo cambia el estatus
     * del contrato (importe 0), ni al registrarse ni al cancelarse.
     */
    public static final Set<TipoMovimiento> TIPOS_SIN_CAJA = EnumSet.of(TipoMovimiento.PV, TipoMovimiento.PVA);

    private final TipoMovimientoCaja tipo;
    /** Null en las cancelaciones: su renglón depende de la operación original. */
    private final RenglonCorte renglonCorte;

    ConceptoCaja(TipoMovimientoCaja tipo, RenglonCorte renglonCorte) {
        this.tipo = tipo;
        this.renglonCorte = renglonCorte;
    }

    /** ENTRADA o SALIDA de la gaveta; {@code MovimientoCajaService} la toma de aquí. */
    public TipoMovimientoCaja tipo() {
        return tipo;
    }

    /**
     * Si el concepto es la cancelación de otra operación (Q-C1). Una cancelación no tiene renglón
     * propio: resta, con signo contrario, del renglón de la operación original.
     *
     * @return {@code true} para {@link #CANCELACION_EMPENO}, {@link #DEVOLUCION_COBRO} y
     *         {@link #CANCELACION_VENTA}
     */
    public boolean esCancelacion() {
        return renglonCorte == null;
    }

    /**
     * Renglón del corte de caja donde se suma este concepto (sección 3 de PLAN-CORTE-CAJA). En
     * {@link #ABONO_CAPITAL} es el renglón de la parte de capital.
     *
     * @return renglón del corte
     * @throws IllegalStateException si el concepto es una cancelación: su renglón sale del origen
     *                               ({@code ResolutorRenglonCorte})
     */
    public RenglonCorte renglonCorte() {
        if (renglonCorte == null) {
            throw new IllegalStateException("La cancelación " + name()
                    + " no tiene renglón propio: se resuelve con la operación original");
        }
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
            case RF, RPG, RP -> REFRENDO;
            case RX, RPX -> REFRENDO_EXTEMPORANEO;
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
