package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.request.ParametrosSistemaRequest;
import com.ignis.prestamil.response.ParametrosSistemaResponse;
import org.springframework.stereotype.Component;

@Component
public class ParametrosSistemaMapper {

    /**
     * Convierte un ParametrosSistema a ParametrosSistemaResponse
     */
    public ParametrosSistemaResponse toParametrosSistemaResponse(ParametrosSistema parametrosSistema) {
        if (parametrosSistema == null) {
            return null;
        }

        ParametrosSistemaResponse response = new ParametrosSistemaResponse();
        response.setId(parametrosSistema.getId());
        response.setDescripcion(parametrosSistema.getDescripcion());
        response.setValorCadena(parametrosSistema.getValorCadena());
        response.setValorNumerico(parametrosSistema.getValorNumerico());
        response.setTipoDatoInterfaz(parametrosSistema.getTipoDatoInterfaz() != null ? parametrosSistema.getTipoDatoInterfaz().name() : null);

        return response;
    }

    /**
     * Convierte un ParametrosSistemaRequest a ParametrosSistema (sin el ID)
     */
    public ParametrosSistema toParametrosSistema(ParametrosSistemaRequest request) {
        if (request == null) {
            return null;
        }

        ParametrosSistema parametrosSistema = new ParametrosSistema();
        parametrosSistema.setDescripcion(request.getDescripcion());
        parametrosSistema.setValorCadena(request.getValorCadena());
        parametrosSistema.setValorNumerico(request.getValorNumerico());
        
        // Convertir String a enum
        if (request.getTipoDatoInterfaz() != null && !request.getTipoDatoInterfaz().isEmpty()) {
            try {
                parametrosSistema.setTipoDatoInterfaz(ParametrosSistema.TipoDatoInterfaz.valueOf(request.getTipoDatoInterfaz()));
            } catch (IllegalArgumentException e) {
                // Si el valor no es válido, se deja como null
                parametrosSistema.setTipoDatoInterfaz(null);
            }
        }

        return parametrosSistema;
    }

}

