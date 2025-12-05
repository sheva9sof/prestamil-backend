package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Configuracion;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionRepository extends BaseRepository<Configuracion, Integer> {

    Optional<Configuracion> findByConfiguracion(String configuracion);
}
