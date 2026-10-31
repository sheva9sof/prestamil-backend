package com.ignis.prestamil.service.calculo;

import java.util.Optional;

/**
 * De dónde sale el saldo de arranque del corte de caja (CAJ-05). Desde CAJ-06 lo lee
 * {@link SaldoArranqueCajaProvider} de {@code caja_arranque}, que Sistemas captura una sola vez por sucursal.
 */
public interface SaldoArranqueProvider {

    /**
     * Saldo de arranque de la sucursal.
     *
     * @param sucursalId identificador de la sucursal
     * @return fecha y saldo de arranque, o vacío si todavía no se capturan
     */
    Optional<SaldoArranque> saldoArranque(Integer sucursalId);
}
