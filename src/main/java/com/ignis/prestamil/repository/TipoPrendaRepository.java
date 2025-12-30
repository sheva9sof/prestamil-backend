package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.TipoPrenda;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoPrendaRepository extends BaseRepository<TipoPrenda, Integer> {

    List<TipoPrenda> findAllByOrderByIdAsc();

}

