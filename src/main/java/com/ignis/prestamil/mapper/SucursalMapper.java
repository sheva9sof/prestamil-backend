package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.response.SucursalResponse;
import org.springframework.stereotype.Component;

@Component
public class SucursalMapper {

    /**
     * Convierte un Sucursal a SucursalResponse
     */
    public SucursalResponse toSucursalResponse(Sucursal sucursal) {
        if (sucursal == null) {
            return null;
        }

        SucursalResponse response = new SucursalResponse();
        response.setId(sucursal.getId());
        response.setNombre(sucursal.getNombre());
        response.setIdRazonSocial(sucursal.getEmpresa() != null ? sucursal.getEmpresa().getId() : null);
        response.setNombreEmpresa(sucursal.getEmpresa() != null ? sucursal.getEmpresa().getNombre() : null);
        response.setRazonSocial(sucursal.getEmpresa() != null ? sucursal.getEmpresa().getRazonSocial() : null);
        response.setCalle(sucursal.getCalle());
        response.setNoExterior(sucursal.getNoExterior());
        response.setNoInterior(sucursal.getNoInterior());
        response.setColonia(sucursal.getColonia());
        response.setMunicipio(sucursal.getMunicipio());
        response.setCp(sucursal.getCp());
        response.setEstado(sucursal.getEstado());
        response.setPais(sucursal.getPais());
        response.setLada(sucursal.getLada());
        response.setTelefono(sucursal.getTelefono());
        response.setFechaApertura(sucursal.getFechaApertura());
        response.setRfc(sucursal.getRfc());
        response.setFechaRegistroProfeco(sucursal.getFechaRegistroProfeco());
        response.setFechaContratoProfeco(sucursal.getFechaContratoProfeco());

        return response;
    }

}

