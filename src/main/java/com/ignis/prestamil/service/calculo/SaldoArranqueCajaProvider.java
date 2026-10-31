package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.repository.CajaArranqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Saldo de arranque de cada sucursal desde {@code caja_arranque} (CAJ-06). Reemplaza la lectura temporal de
 * {@code configuraciones} de CAJ-05: el changeset 086 pasó esos valores a la tabla sin confirmar, y el corte
 * los usa igual hasta que Sistemas captura los definitivos.
 */
@Component
@RequiredArgsConstructor
public class SaldoArranqueCajaProvider implements SaldoArranqueProvider {

    private final CajaArranqueRepository cajaArranqueRepository;

    /**
     * Saldo de arranque de la sucursal, confirmado o no.
     *
     * @param sucursalId identificador de la sucursal
     * @return fecha y saldo de arranque, o vacío si la sucursal no tiene
     */
    @Override
    public Optional<SaldoArranque> saldoArranque(Integer sucursalId) {
        return cajaArranqueRepository.findById(sucursalId)
                .map(a -> new SaldoArranque(a.getFechaArranque(), a.getSaldoBoveda()));
    }
}
