package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Opcion;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpcionRepository extends BaseRepository<Opcion, Integer> {

    List<Opcion> findByIdPadre(Integer idPadre);

    List<Opcion> findByPrincipalMenuTrue();

    List<Opcion> findByEstatusTrue();

}

