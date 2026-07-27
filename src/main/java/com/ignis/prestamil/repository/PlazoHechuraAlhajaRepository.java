package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;

import java.util.List;

public interface PlazoHechuraAlhajaRepository
        extends BaseRepository<PlazoHechuraAlhaja, PlazoHechuraAlhajaId> {

    /**
     * Busca todas las hechuras de alhaja para un plazo y sucursal dados.
     *
     * @param idPlazo    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return lista de PlazoHechuraAlhaja para la combinación
     */
    List<PlazoHechuraAlhaja> findByIdIdPlazoAndIdSucursalId(Integer idPlazo, Integer sucursalId);

    /**
     * Busca todas las hechuras de alhaja de una sucursal (todos los plazos).
     * Usado por el recálculo global cuando cambia el precio del oro.
     *
     * @param sucursalId identificador de la sucursal
     * @return lista de PlazoHechuraAlhaja de la sucursal
     */
    List<PlazoHechuraAlhaja> findByIdSucursalId(Integer sucursalId);

    void deleteByIdIdPlazo(Integer idPlazo);
}
