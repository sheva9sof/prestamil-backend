package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.response.TipoPrendaResponse;
import org.springframework.stereotype.Component;

@Component
public class TipoPrendaMapper {

    /**
     * Convierte un TipoPrenda a TipoPrendaResponse
     */
    public TipoPrendaResponse toTipoPrendaResponse(TipoPrenda tipoPrenda) {
        if (tipoPrenda == null) {
            return null;
        }

        TipoPrendaResponse response = new TipoPrendaResponse();
        response.setId(tipoPrenda.getId());
        response.setTipo(tipoPrenda.getTipo());
        return response;
    }

}

