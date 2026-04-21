package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.response.CatSubtipoPrendaResponse;
import com.ignis.prestamil.response.CatValorPrendaResponse;
import com.ignis.prestamil.response.TipoPrendaResponse;
import org.springframework.stereotype.Component;

@Component
public class PrendaMapper {

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

    /**
     * Convierte un CatSubtipoPrenda a CatSubtipoPrendaResponse
     */
    public CatSubtipoPrendaResponse toCatSubtipoPrendaResponse(CatSubtipoPrenda catSubtipoPrenda) {
        if (catSubtipoPrenda == null) {
            return null;
        }

        CatSubtipoPrendaResponse response = new CatSubtipoPrendaResponse();
        response.setIdAtributo(catSubtipoPrenda.getIdAtributo());
        response.setIdTipoPrenda(catSubtipoPrenda.getTipoPrenda() != null ? catSubtipoPrenda.getTipoPrenda().getId() : null);
        response.setNombreAtributo(catSubtipoPrenda.getNombreAtributo());
        return response;
    }

    /**
     * Convierte un CatValorPrenda a CatValorPrendaResponse
     */
    public CatValorPrendaResponse toCatValorPrendaResponse(CatValorPrenda catValorPrenda) {
        if (catValorPrenda == null) {
            return null;
        }

        CatValorPrendaResponse response = new CatValorPrendaResponse();
        response.setIdValorAtributo(catValorPrenda.getIdValorAtributo());
        response.setClave(catValorPrenda.getClave());
        response.setDescripcion(catValorPrenda.getDescripcion());
        response.setKilataje(catValorPrenda.getKilataje());
        response.setContienePiedad(catValorPrenda.getContienePiedad());

        // Información del subtipo de prenda
        if (catValorPrenda.getSubtipoPrenda() != null) {
            CatSubtipoPrenda subtipo = catValorPrenda.getSubtipoPrenda();
            response.setIdAtributo(subtipo.getIdAtributo());
            response.setNombreAtributo(subtipo.getNombreAtributo());

            // Información del tipo de prenda
            if (subtipo.getTipoPrenda() != null) {
                TipoPrenda tipoPrenda = subtipo.getTipoPrenda();
                response.setIdTipoPrenda(tipoPrenda.getId());
                response.setTipo(tipoPrenda.getTipo());
            }
        }

        return response;
    }

}

