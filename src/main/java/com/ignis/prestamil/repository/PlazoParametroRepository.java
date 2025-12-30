package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.PlazoParametroId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlazoParametroRepository extends BaseRepository<PlazoParametro, PlazoParametroId> {

    List<PlazoParametro> findByPlazoId(Long plazoId);

    List<PlazoParametro> findByTipoPrendaId(Integer tipoPrendaId);

    Optional<PlazoParametro> findByPlazoIdAndTipoPrendaId(Long plazoId, Integer tipoPrendaId);

}

