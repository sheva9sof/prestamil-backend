package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Catalogo;
import com.ignis.prestamil.response.CatalogoResponse;
import org.springframework.stereotype.Component;

@Component
public class CatalogoMapper {

    /**
     * Convierte un Catalogo a CatalogoResponse
     */
    public CatalogoResponse toCatalogoResponse(Catalogo catalogo) {
        if (catalogo == null) {
            return null;
        }

        CatalogoResponse response = new CatalogoResponse();
        response.setId(catalogo.getId());
        response.setNombre(catalogo.getNombre());
        response.setIdTipoCatalogo(catalogo.getIdTipoCatalogo());
        response.setValorCadena(catalogo.getValorCadena());
        response.setValorNumerico(catalogo.getValorNumerico());
        response.setValorFecha(catalogo.getValorFecha());
        response.setEsEditable(catalogo.getEsEditable());
        response.setDescrtipcion(catalogo.getDescrtipcion());

        return response;
    }

}

