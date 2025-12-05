package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class ConfiguracionService extends BaseService<Configuracion, Integer, ConfiguracionRepository> {

    public ConfiguracionService(ConfiguracionRepository repository) {
        super(repository);
    }

    public Optional<Configuracion> findByConfiguracion(String configuracion) {
        return repository.findByConfiguracion(configuracion);
    }
}
