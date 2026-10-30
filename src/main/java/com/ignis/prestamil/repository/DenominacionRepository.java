package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Denominacion;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DenominacionRepository extends BaseRepository<Denominacion, Integer> {

    List<Denominacion> findAllByOrderByOrdenAsc();

    /** Denominaciones que aparecen en el desglose de traspasos y en el arqueo. */
    List<Denominacion> findByActivaTrueOrderByOrdenAsc();
}
