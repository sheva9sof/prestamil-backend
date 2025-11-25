package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.EmpresaMapper;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.repository.EmpresaRepository;
import com.ignis.prestamil.request.EmpresaRequest;
import com.ignis.prestamil.response.EmpresaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmpresaService extends BaseService<Empresa, Integer, EmpresaRepository> {

    private final EmpresaMapper empresaMapper;

    public EmpresaService(EmpresaRepository repository, EmpresaMapper empresaMapper) {
        super(repository);
        this.empresaMapper = empresaMapper;
    }

    /**
     * Crea una nueva empresa
     *
     * @param request DTO con los datos de la nueva empresa
     * @return EmpresaResponse con los datos de la empresa creada
     */
    public EmpresaResponse create(EmpresaRequest request) {
        // Convertir request a entidad
        Empresa empresa = empresaMapper.toEmpresa(request);

        // Guardar en base de datos
        Empresa saved = super.save(empresa);

        // Convertir entidad a response y retornar
        return empresaMapper.toEmpresaResponse(saved);
    }

    /**
     * Actualiza una empresa existente
     *
     * @param id ID de la empresa a actualizar
     * @param request DTO con los datos a actualizar
     * @return EmpresaResponse con los datos actualizados
     */
    public EmpresaResponse update(Integer id, EmpresaRequest request) {
        // Convertir request a entidad
        Empresa empresa = empresaMapper.toEmpresa(request);
        empresa.setId(id);

        // Actualizar en base de datos
        Empresa updated = super.update(empresa);

        // Convertir entidad a response y retornar
        return empresaMapper.toEmpresaResponse(updated);
    }

}

