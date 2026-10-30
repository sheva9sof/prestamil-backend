package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Sucursal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SucursalRepository extends BaseRepository<Sucursal, Integer> {

    /**
     * Sucursal con bloqueo de escritura ({@code SELECT ... FOR UPDATE}). El pase de almoneda lo usa
     * para que dos ejecuciones simultaneas de la misma sucursal se formen en fila: la segunda espera
     * y luego ve que el dia ya corrio.
     *
     * @param id sucursal
     * @return la sucursal bloqueada hasta el fin de la transaccion
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Sucursal> findWithLockById(Integer id);
}

