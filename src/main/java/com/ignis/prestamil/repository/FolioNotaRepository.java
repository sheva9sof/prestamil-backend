package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.FolioNota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioNotaRepository extends BaseRepository<FolioNota, Integer> {

    /**
     * Contador de la sucursal bloqueado hasta el fin de la transacción, para que dos cobros
     * simultáneos no tomen el mismo folio.
     *
     * @param sucursalId identificador de la sucursal
     * @return el contador, o vacío si la sucursal aún no tiene
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FolioNota> findBySucursalId(Integer sucursalId);
}
