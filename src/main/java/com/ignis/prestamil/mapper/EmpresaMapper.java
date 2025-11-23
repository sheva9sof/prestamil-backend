package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.response.EmpresaResponse;
import org.springframework.stereotype.Component;

@Component
public class EmpresaMapper {

    /**
     * Convierte un Empresa a EmpresaResponse
     */
    public EmpresaResponse toEmpresaResponse(Empresa empresa) {
        if (empresa == null) {
            return null;
        }

        EmpresaResponse response = new EmpresaResponse();
        response.setId(empresa.getId());
        response.setNombre(empresa.getNombre());
        response.setRazonSocial(empresa.getRazonSocial());

        return response;
    }

}

