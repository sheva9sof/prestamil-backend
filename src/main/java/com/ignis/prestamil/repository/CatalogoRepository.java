package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Catalogo;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public interface CatalogoRepository extends BaseRepository<Catalogo, Integer> {

    List<Catalogo> findByIdTipoCatalogo(Integer idTipoCatalogo);
}

