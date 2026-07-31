package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.request.PlazoHechuraAlhajaRequest;
import com.ignis.prestamil.response.PlazoHechuraAlhajaResponse;
import org.springframework.stereotype.Component;

@Component
public class PlazoHechuraAlhajaMapper {

    /**
     * Convierte un PlazoHechuraAlhaja a PlazoHechuraAlhajaResponse.
     *
     * @param entity entidad a convertir
     * @return DTO de respuesta con descripción de hechura incluida
     */
    public PlazoHechuraAlhajaResponse toResponse(PlazoHechuraAlhaja entity) {
        if (entity == null) return null;
        PlazoHechuraAlhajaResponse r = new PlazoHechuraAlhajaResponse();
        r.setIdPlazo(entity.getId().getIdPlazo());
        r.setSucursalId(entity.getId().getSucursalId());
        r.setKilataje(entity.getId().getKilataje());
        r.setHechura(entity.getId().getHechura());
        r.setHechuraDescripcion(describirHechura(entity.getId().getHechura()));
        r.setPrecioBase(entity.getPrecioBase());
        r.setPorcAumento(entity.getPorcAumento());
        r.setPrecioPrestamo(entity.getPrecioPrestamo());
        return r;
    }

    /**
     * Convierte un PlazoHechuraAlhajaRequest a entidad (sin precio_prestamo calculado — el servicio lo calcula).
     *
     * @param req        DTO de entrada
     * @param idPlazo    identificador del plazo (de path variable)
     * @param sucursalId identificador de la sucursal (de query param)
     * @return entidad lista para persistir
     */
    public PlazoHechuraAlhaja toEntity(PlazoHechuraAlhajaRequest req, Integer idPlazo, Integer sucursalId) {
        PlazoHechuraAlhaja e = new PlazoHechuraAlhaja();
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, req.getKilataje(), req.getHechura());
        e.setId(id);
        e.setPrecioBase(req.getPrecioBase());
        e.setPorcAumento(req.getPorcAumento());
        // precio_prestamo no se setea aquí — se calcula en el servicio
        return e;
    }

    /**
     * Devuelve la descripción legible de la clave de hechura.
     *
     * @param h clave de hechura ("F", "N" o "E")
     * @return descripción en español
     */
    private String describirHechura(String h) {
        if (h == null) return "";
        return switch (h) {
            case "F" -> "Fina";
            case "N" -> "Normal";
            case "E" -> "Especial";
            default -> h;
        };
    }
}
