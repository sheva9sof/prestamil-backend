package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Sucursal;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SucursalRepository extends BaseRepository<Sucursal, Integer> {

    /**
     * Obtiene la única sucursal de la base de datos (primera encontrada)
     * @return Optional con la sucursal si existe
     */
    Optional<Sucursal> findFirstByOrderByIdAsc();

}

