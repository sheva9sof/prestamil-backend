package com.ignis.prestamil.service;

import com.ignis.prestamil.model.RenglonCorte;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo que un flujo de caja aporta a un renglón del corte (CAJ-01d). Un flujo da una parte, salvo el RC,
 * que da dos (capital y refrendo). Los importes llevan signo: positivo suma al renglón y negativo resta,
 * que es como queda una cancelación (Q-C1). El renglón dice si la parte es ingreso o egreso.
 *
 * @param renglon  renglón del corte
 * @param efectivo parte en efectivo, neta del cambio; la que entra al arqueo (RC-06)
 * @param tarjeta  parte con tarjeta; informativa
 */
public record ParteCorte(RenglonCorte renglon, BigDecimal efectivo, BigDecimal tarjeta) {

    public ParteCorte {
        Objects.requireNonNull(renglon, "renglon");
        Objects.requireNonNull(efectivo, "efectivo");
        Objects.requireNonNull(tarjeta, "tarjeta");
    }

    /** Renglón en cero, punto de partida para sumar partes. */
    public static ParteCorte cero(RenglonCorte renglon) {
        return new ParteCorte(renglon, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }

    /** Efectivo más tarjeta. */
    public BigDecimal total() {
        return efectivo.add(tarjeta);
    }

    /** La misma parte con signo contrario: lo que aporta la cancelación de este flujo. */
    public ParteCorte negada() {
        return new ParteCorte(renglon, efectivo.negate(), tarjeta.negate());
    }

    /**
     * Suma otra parte del mismo renglón.
     *
     * @param otra parte a sumar
     * @return la suma de efectivo y tarjeta
     * @throws IllegalArgumentException si la otra parte es de otro renglón
     */
    public ParteCorte mas(ParteCorte otra) {
        if (otra.renglon != renglon) {
            throw new IllegalArgumentException("No se suman partes de renglones distintos: "
                    + renglon + " y " + otra.renglon);
        }
        return new ParteCorte(renglon, efectivo.add(otra.efectivo), tarjeta.add(otra.tarjeta));
    }
}
