package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.response.RolResponse;
import org.springframework.stereotype.Component;

@Component
public class RolMapper {

    /**
     * Convierte un Rol a RolResponse
     * El campo "rol" del modelo se mapea al campo "nombre" del response
     */
    public RolResponse toRolResponse(Rol rol) {
        if (rol == null) {
            return null;
        }

        RolResponse response = new RolResponse();
        response.setId(rol.getId());
        response.setNombre(rol.getRol()); // Mapeo especial: rol -> nombre
        // Nota: RolResponse tiene un campo "descripcion" que no está en el modelo Rol
        return response;
    }
}
