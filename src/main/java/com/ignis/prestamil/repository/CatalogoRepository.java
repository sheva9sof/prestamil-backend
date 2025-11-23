package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Catalogo;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogoRepository extends BaseRepository<Catalogo, Integer> {

    Optional<Catalogo> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

}

