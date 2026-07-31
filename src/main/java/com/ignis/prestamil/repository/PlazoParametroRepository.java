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

    /**
     * Lista parámetros de un plazo filtrados por sucursal.
     *
     * @param plazoId    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return lista de PlazoParametro para la sucursal indicada
     */
    List<PlazoParametro> findByPlazoIdAndSucursalId(Long plazoId, Integer sucursalId);

    /**
     * Busca el parámetro exacto para plazo + tipo de prenda + sucursal.
     *
     * @param plazoId      identificador del plazo
     * @param tipoPrendaId identificador del tipo de prenda
     * @param sucursalId   identificador de la sucursal
     * @return Optional con el PlazoParametro si existe
     */
    Optional<PlazoParametro> findByPlazoIdAndTipoPrendaIdAndSucursalId(
            Long plazoId, Integer tipoPrendaId, Integer sucursalId);

    void deleteByPlazoId(Long plazoId);
}
