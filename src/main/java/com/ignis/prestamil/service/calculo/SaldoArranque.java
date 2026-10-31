package com.ignis.prestamil.service.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Punto de partida del corte de caja de una sucursal (B-04): el saldo de la bóveda al empezar el día de
 * arranque, que es el día de la migración. Antes de esa fecha el sistema no tiene cifras de caja.
 *
 * @param fecha día de arranque; su saldo anterior es {@code saldo}
 * @param saldo saldo de la bóveda al abrir el día de arranque
 */
public record SaldoArranque(LocalDate fecha, BigDecimal saldo) {

    public SaldoArranque {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(saldo, "saldo");
    }
}
