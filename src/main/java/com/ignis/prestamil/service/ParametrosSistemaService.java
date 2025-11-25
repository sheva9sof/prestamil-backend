package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.ParametrosSistemaMapper;
import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.request.ParametrosSistemaRequest;
import com.ignis.prestamil.response.ParametrosSistemaResponse;
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

