package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.request.EmpresaRequest;
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
        response.setCalle(empresa.getCalle());
        response.setNoExterior(empresa.getNoExterior());
        response.setNoInterior(empresa.getNoInterior());
        response.setColonia(empresa.getColonia());
        response.setDelegacion(empresa.getDelegacion());
        response.setCp(empresa.getCp());
        response.setEstado(empresa.getEstado());
        response.setPais(empresa.getPais());

        return response;
    }

    /**
     * Convierte un EmpresaRequest a Empresa (sin el ID)
     */
    public Empresa toEmpresa(EmpresaRequest request) {
        if (request == null) {
            return null;
        }

        Empresa empresa = new Empresa();
        empresa.setNombre(request.getNombre());
        empresa.setRazonSocial(request.getRazonSocial());
        empresa.setCalle(request.getCalle());
        empresa.setNoExterior(request.getNoExterior());
        empresa.setNoInterior(request.getNoInterior());
        empresa.setColonia(request.getColonia());
        empresa.setDelegacion(request.getDelegacion());
        empresa.setCp(request.getCp());
        empresa.setEstado(request.getEstado());
        empresa.setPais(request.getPais());
        return empresa;
    }

}

