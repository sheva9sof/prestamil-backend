package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.FolioBolsa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioBolsaRepository extends BaseRepository<FolioBolsa, Integer> {

    /**
     * Contador de bolsas de la sucursal bloqueado hasta el fin de la transacción, para que dos
     * contratos simultáneos no tomen la misma bolsa.
     *
     * @param sucursalId identificador de la sucursal
     * @return el contador, o vacío si la sucursal aún no tiene
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FolioBolsa> findBySucursalId(Integer sucursalId);
}
