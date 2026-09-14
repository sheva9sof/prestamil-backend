package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.ParametrosSistemaMapper;
import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.request.ParametrosSistemaRequest;
import com.ignis.prestamil.response.ParametrosSistemaResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ParametrosSistemaService extends BaseService<ParametrosSistema, Integer, ParametrosSistemaRepository> {

    private final ParametrosSistemaMapper parametrosSistemaMapper;

    public ParametrosSistemaService(ParametrosSistemaRepository repository, ParametrosSistemaMapper parametrosSistemaMapper) {
        super(repository);
        this.parametrosSistemaMapper = parametrosSistemaMapper;
    }

    /**
     * Actualiza un parámetro del sistema existente
     * 
     * @param id ID del parámetro a actualizar
     * @param request DTO con los datos a actualizar
     * @return ParametrosSistemaResponse con los datos actualizados
     */
    // El evict del cache de IVA se dispara para CUALQUIER update de parametros_sistema. Preferimos
    // evictar de mas (el cache se llena con 1 miss en el siguiente refrendo/PDF) a olvidar evictar
    // cuando se edita el registro id=8 y quedar sirviendo IVA obsoleto.
    @CacheEvict(cacheNames = "parametrosSistemaIva", allEntries = true)
    public ParametrosSistemaResponse update(Integer id, ParametrosSistemaRequest request) {
        // Convertir request a entidad
        ParametrosSistema parametrosSistema = parametrosSistemaMapper.toParametrosSistema(request);
        parametrosSistema.setId(id);

        // Actualizar en base de datos
        ParametrosSistema updated = super.update(parametrosSistema);

        // Convertir entidad a response y retornar
        return parametrosSistemaMapper.toParametrosSistemaResponse(updated);
    }

}

