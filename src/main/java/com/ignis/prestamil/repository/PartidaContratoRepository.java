package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PartidaContrato;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartidaContratoRepository extends BaseRepository<PartidaContrato, Long> {

    List<PartidaContrato> findByContratoIdOrderByNumPartidaAsc(Long contratoId);

    /** Cuenta las partidas que referencian un valor del catálogo, para impedir su borrado físico. */
    long countByValorPrendaIdValorAtributo(Integer idValorAtributo);
}
