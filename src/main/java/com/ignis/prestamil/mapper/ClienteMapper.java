package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.response.ClienteResponse;
import com.ignis.prestamil.response.DireccionResponse;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

    /**
     * Convierte un Cliente a ClienteResponse
     */
    public ClienteResponse toClienteResponse(Cliente cliente) {
        if (cliente == null) {
            return null;
        }

        ClienteResponse response = new ClienteResponse();
        response.setId(cliente.getId());
        response.setNombre(cliente.getNombre());
        response.setApellidoPaterno(cliente.getApellidoPaterno());
        response.setApellidoMaterno(cliente.getApellidoMaterno());
        response.setTelefono(cliente.getTelefono());
        response.setCurp(cliente.getCurp());
        response.setRfc(cliente.getRfc());
        response.setActivo(cliente.getActivo());
        response.setDireccionId(cliente.getDireccion() != null ? cliente.getDireccion().getId() : null);
        
        // Mapear dirección si está cargada
        if (cliente.getDireccion() != null) {
            response.setDireccion(toDireccionResponse(cliente.getDireccion()));
        }

        return response;
    }

    /**
     * Convierte una Direccion a DireccionResponse
     */
    public DireccionResponse toDireccionResponse(Direccion direccion) {
        if (direccion == null) {
            return null;
        }

        DireccionResponse response = new DireccionResponse();
        response.setId(direccion.getId());
        response.setTipoDireccion(direccion.getTipoDireccion() != null ? direccion.getTipoDireccion().name() : null);
        response.setCalle(direccion.getCalle());
        response.setNumeroExterior(direccion.getNumeroExterior());
        response.setNumeroInterior(direccion.getNumeroInterior());
        response.setColonia(direccion.getColonia());
        response.setCiudad(direccion.getCiudad());
        response.setEstado(direccion.getEstado());
        response.setCodigoPostal(direccion.getCodigoPostal());
        response.setReferencias(direccion.getReferencias());
        response.setEsVerificada(direccion.getEsVerificada());
        response.setFechaRegistro(direccion.getFechaRegistro());

        return response;
    }

}

