package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Rol;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolRepository extends BaseRepository<Rol, Integer> {

    Optional<Rol> findByRol(String rol);

    boolean existsByRol(String rol);

    List<Rol> findByEstatusTrue();

}

