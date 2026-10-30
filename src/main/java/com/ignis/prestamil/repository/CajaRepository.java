package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.TipoCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends BaseRepository<Caja, Integer> {

    List<Caja> findBySucursalIdOrderByClaveAsc(Integer sucursalId);

    /**
     * Caja activa de un tipo en la sucursal. Con una sola VENTANILLA por sucursal (H-05) el resultado es
     * único para los tres tipos; cuando haya varias ventanillas, la de un turno saldrá del turno (B-05).
     */
    Optional<Caja> findFirstBySucursalIdAndTipoAndActivaTrueOrderByClaveAsc(Integer sucursalId, TipoCaja tipo);

    /**
     * Caja bloqueada hasta el fin de la transacción. Se toma antes de revisar su saldo, para que dos
     * operaciones simultáneas que sacan dinero de la misma caja no validen contra el mismo saldo.
     *
     * @param id identificador de la caja
     * @return la caja, o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Caja c WHERE c.id = :id")
    Optional<Caja> findByIdParaActualizar(@Param("id") Integer id);
}
