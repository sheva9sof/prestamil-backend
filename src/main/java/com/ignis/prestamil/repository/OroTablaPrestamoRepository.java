package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.OroTablaPrestamo;
import com.ignis.prestamil.model.OroTablaPrestamoId;

import java.util.List;

public interface OroTablaPrestamoRepository
        extends BaseRepository<OroTablaPrestamo, OroTablaPrestamoId> {

    /**
     * Busca las 24 celdas de %Prestamo de una sucursal (todos los kilates/hechuras).
     * Usado por PlazoService.recalcularRegistros para derivar precio_base por celda.
     *
     * @param sucursalId identificador de la sucursal
     * @return lista de OroTablaPrestamo de la sucursal
     */
    List<OroTablaPrestamo> findByIdSucursalId(Integer sucursalId);
}
