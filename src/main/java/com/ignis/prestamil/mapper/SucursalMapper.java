package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.request.SucursalRequest;
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
        response.setNumeroSucursal(sucursal.getNumeroSucursal());
        response.setNombre(sucursal.getNombre());
        response.setIdRazonSocial(sucursal.getEmpresa() != null ? sucursal.getEmpresa().getId() : null);
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
        response.setLunes(sucursal.getLunes());
        response.setMartes(sucursal.getMartes());
        response.setMiercoles(sucursal.getMiercoles());
        response.setJueves(sucursal.getJueves());
        response.setViernes(sucursal.getViernes());
        response.setSabado(sucursal.getSabado());
        response.setDomingo(sucursal.getDomingo());
        response.setHorarioAtencion(sucursal.getHorarioAtencion());

        if (sucursal.getEmpresa() != null) {
            response.setNombreEmpresa(sucursal.getEmpresa().getNombre());
            response.setRazonSocial(sucursal.getEmpresa().getRazonSocial());
        }

        return response;
    }

    /**
     * Convierte un SucursalRequest a Sucursal (sin el ID)
     * Nota: La empresa se debe cargar en el servicio usando idRazonSocial
     */
    public Sucursal toSucursal(SucursalRequest request) {
        if (request == null) {
            return null;
        }

        Sucursal sucursal = new Sucursal();
        sucursal.setNumeroSucursal(request.getNumeroSucursal());
        sucursal.setNombre(request.getNombre());
        sucursal.setCalle(request.getCalle());
        sucursal.setNoExterior(request.getNoExterior());
        sucursal.setNoInterior(request.getNoInterior());
        sucursal.setColonia(request.getColonia());
        sucursal.setMunicipio(request.getMunicipio());
        sucursal.setCp(request.getCp());
        sucursal.setEstado(request.getEstado());
        sucursal.setPais(request.getPais());
        sucursal.setLada(request.getLada());
        sucursal.setTelefono(request.getTelefono());
        sucursal.setFechaApertura(request.getFechaApertura());
        sucursal.setRfc(request.getRfc());
        sucursal.setFechaRegistroProfeco(request.getFechaRegistroProfeco());
        sucursal.setFechaContratoProfeco(request.getFechaContratoProfeco());
        sucursal.setLunes(request.getLunes());
        sucursal.setMartes(request.getMartes());
        sucursal.setMiercoles(request.getMiercoles());
        sucursal.setJueves(request.getJueves());
        sucursal.setViernes(request.getViernes());
        sucursal.setSabado(request.getSabado());
        sucursal.setDomingo(request.getDomingo());
        sucursal.setHorarioAtencion(request.getHorarioAtencion());
        return sucursal;
    }

}

