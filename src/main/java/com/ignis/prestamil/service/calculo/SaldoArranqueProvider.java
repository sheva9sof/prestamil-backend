package com.ignis.prestamil.service.calculo;

import java.util.Optional;

/**
 * De dónde sale el saldo de arranque del corte de caja (CAJ-05). La implementación temporal lo lee de
 * {@code configuraciones}; CAJ-06 la reemplaza por la captura de Sistemas, una sola vez por sucursal.
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
