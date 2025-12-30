package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.CatValorPrenda;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatValorPrendaRepository extends BaseRepository<CatValorPrenda, Integer> {

    List<CatValorPrenda> findBySubtipoPrendaIdAtributoOrderByIdValorAtributoAsc(Integer idAtributo);

    @EntityGraph(attributePaths = {"subtipoPrenda", "subtipoPrenda.tipoPrenda"})
    List<CatValorPrenda> findWithSubtipoAndTipoBySubtipoPrendaIdAtributoOrderByIdValorAtributoAsc(Integer idAtributo);

}

