package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PrecioOro;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrecioOroRepository extends BaseRepository<PrecioOro, Integer> {

    /**
     * Obtiene el precio del oro vigente para una sucursal.
     *
     * @param sucursalId identificador de la sucursal
     * @return Optional con el PrecioOro si existe
     */
    Optional<PrecioOro> findBySucursalId(Integer sucursalId);
}
