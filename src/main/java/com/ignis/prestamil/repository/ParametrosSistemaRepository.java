package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.ParametrosSistema;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametrosSistemaRepository extends BaseRepository<ParametrosSistema, Integer> {

    Optional<ParametrosSistema> findByDescripcion(String descripcion);

    boolean existsByDescripcion(String descripcion);

}

