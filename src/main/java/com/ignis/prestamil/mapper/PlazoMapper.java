package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.request.PlazoRequest;
import com.ignis.prestamil.response.PlazoResponse;
import com.ignis.prestamil.response.TipoPrendaResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlazoMapper {

    private final PrendaMapper prendaMapper;

    public PlazoMapper(PrendaMapper prendaMapper) {
        this.prendaMapper = prendaMapper;
    }

    /**
     * Convierte un Plazo a PlazoResponse
     */
    public PlazoResponse toPlazoResponse(Plazo plazo) {
        if (plazo == null) {
            return null;
        }

        PlazoResponse response = new PlazoResponse();
        response.setId(plazo.getId());
        response.setNombre(plazo.getNombre());
        response.setDiasPorPeriodo(plazo.getDiasPorPeriodo());
        response.setNumeroPeriodos(plazo.getNumeroPeriodos());
        response.setActivo(plazo.getActivo());
        response.setCreadoEn(plazo.getCreadoEn());
        response.setActualizadoEn(plazo.getActualizadoEn());

        // Mapear tipos de prenda
        if (plazo.getTiposPrenda() != null && !plazo.getTiposPrenda().isEmpty()) {
            List<TipoPrendaResponse> tiposPrendaResponse = plazo.getTiposPrenda().stream()
                    .map(prendaMapper::toTipoPrendaResponse)
                    .collect(Collectors.toList());
            response.setTiposPrenda(tiposPrendaResponse);
        } else {
            response.setTiposPrenda(new ArrayList<>());
        }

        return response;
    }

    /**
     * Convierte un PlazoRequest a Plazo (sin el ID y sin los timestamps)
     * El campo activo tiene un valor por defecto de true si es null
     * Los campos creadoEn y actualizadoEn se ignoran ya que se manejan automáticamente
     * tiposPrenda (lista de IDs en el request) se resuelve en el servicio
     */
    public Plazo toPlazo(PlazoRequest request) {
        if (request == null) {
            return null;
        }

        Plazo plazo = new Plazo();
        plazo.setNombre(request.getNombre());
        plazo.setDiasPorPeriodo(request.getDiasPorPeriodo());
        plazo.setNumeroPeriodos(request.getNumeroPeriodos());
        // El campo activo tiene un valor por defecto de true si es null
        plazo.setActivo(request.getActivo() != null ? request.getActivo() : true);
        // Los campos creadoEn y actualizadoEn se ignoran ya que se manejan automáticamente
        // tiposPrenda (IDs del request) se cargan en el servicio
        plazo.setTiposPrenda(new ArrayList<>());

        return plazo;
    }

}

