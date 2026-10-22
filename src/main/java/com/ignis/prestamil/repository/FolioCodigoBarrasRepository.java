package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.FolioCodigoBarras;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioCodigoBarrasRepository extends BaseRepository<FolioCodigoBarras, Integer> {

    /**
     * Contador global de códigos de barras bloqueado hasta el fin de la transacción.
     *
     * @param id siempre {@link FolioCodigoBarras#ID_UNICO}
     * @return el contador, o vacío si aún no existe la fila
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FolioCodigoBarras> findWithLockById(Integer id);
}
