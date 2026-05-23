package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.PartidaContratoResponse;
import org.springframework.stereotype.Component;

@Component
public class ContratoMapper {

    public ContratoResponse toResponse(Contrato contrato) {
        if (contrato == null) return null;

        ContratoResponse r = new ContratoResponse();
        r.setId(contrato.getId());
        r.setFolio(contrato.getFolio());

        if (contrato.getCliente() != null) {
            Cliente c = contrato.getCliente();
            r.setIdCliente(c.getId());
            r.setNombreCliente(c.getNombre() + " " + c.getApellidoPaterno() + " " + c.getApellidoMaterno());
        }

        if (contrato.getPlazo() != null) {
            r.setIdPlazo(contrato.getPlazo().getId());
            r.setNombrePlazo(contrato.getPlazo().getNombre());
        }

        r.setTipoIdentificacion(contrato.getTipoIdentificacion());
        r.setNumIdentificacion(contrato.getNumIdentificacion());
        r.setNombreBeneficiario(contrato.getNombreBeneficiario());
        r.setFechaApertura(contrato.getFechaApertura());
        r.setFechaVencimiento(contrato.getFechaVencimiento());
        r.setMontoPrestamo(contrato.getMontoPrestamo());
        r.setMontoAvaluo(contrato.getMontoAvaluo());
        r.setEstatus(contrato.getEstatus());
        r.setNumRefrendos(contrato.getNumRefrendos());
        r.setCreadoEn(contrato.getCreadoEn());

        if (contrato.getPartidas() != null) {
            r.setPartidas(contrato.getPartidas().stream()
                    .map(this::toPartidaResponse)
                    .toList());
        }

        return r;
    }

    public PartidaContratoResponse toPartidaResponse(PartidaContrato p) {
        if (p == null) return null;

        PartidaContratoResponse r = new PartidaContratoResponse();
        r.setId(p.getId());
        r.setNumPartida(p.getNumPartida());

        if (p.getTipoPrenda() != null) {
            r.setIdTipoPrenda(p.getTipoPrenda().getId());
            r.setTipoPrendaNombre(p.getTipoPrenda().getTipo());
        }

        if (p.getValorPrenda() != null) {
            r.setIdValorPrenda(p.getValorPrenda().getIdValorAtributo());
        }

        r.setClavePrenda(p.getClavePrenda());
        r.setDescripcion(p.getDescripcion());
        r.setCantidad(p.getCantidad());
        r.setPesoGramos(p.getPesoGramos());
        r.setKilataje(p.getKilataje());
        r.setHechura(p.getHechura());
        r.setPrecioXGramo(p.getPrecioXGramo());
        r.setAvaluoReal(p.getAvaluoReal());
        r.setAvaluoContrato(p.getAvaluoContrato());
        r.setMontoPrestamo(p.getMontoPrestamo());
        r.setSubtipo(p.getSubtipo());
        r.setMarca(p.getMarca());
        r.setModelo(p.getModelo());
        r.setSerieImei(p.getSerieImei());
        r.setEstadoFisico(p.getEstadoFisico());

        return r;
    }
}
