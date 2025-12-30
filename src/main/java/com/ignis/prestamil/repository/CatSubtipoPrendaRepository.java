package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.CatSubtipoPrenda;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatSubtipoPrendaRepository extends BaseRepository<CatSubtipoPrenda, Integer> {

    List<CatSubtipoPrenda> findByTipoPrendaIdOrderByIdAtributoAsc(Integer idTipoPrenda);

}

