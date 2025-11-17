package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.RolOpcion;
import com.ignis.prestamil.model.RolOpcionId;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolOpcionRepository extends BaseRepository<RolOpcion, RolOpcionId> {

    List<RolOpcion> findByIdRol(Integer idRol);

    List<RolOpcion> findByIdOpcion(Integer idOpcion);

    void deleteByIdRol(Integer idRol);

    void deleteByIdOpcion(Integer idOpcion);

}

