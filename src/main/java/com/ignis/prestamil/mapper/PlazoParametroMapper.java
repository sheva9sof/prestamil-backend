package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.request.PlazoParametroRequest;
import com.ignis.prestamil.response.PlazoParametroResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PlazoParametroMapper {

    private final PrendaMapper prendaMapper;

    public PlazoParametroMapper(PrendaMapper prendaMapper) {
        this.prendaMapper = prendaMapper;
    }

    /**
     * Convierte un PlazoParametro a PlazoParametroResponse
     */
    public PlazoParametroResponse toPlazoParametroResponse(PlazoParametro plazoParametro) {
        if (plazoParametro == null) {
            return null;
        }

        PlazoParametroResponse response = new PlazoParametroResponse();
        response.setPlazoId(plazoParametro.getPlazoId());
        response.setTipoPrendaId(plazoParametro.getTipoPrendaId());
        
        // Mapear tipo de prenda si está disponible
        if (plazoParametro.getTipoPrenda() != null) {
            response.setTipoPrenda(prendaMapper.toTipoPrendaResponse(plazoParametro.getTipoPrenda()));
        }
        
        response.setPorcInteres(plazoParametro.getPorcInteres());
        response.setPorcAlmacen(plazoParametro.getPorcAlmacen());
        response.setPorcGastosAdmin(plazoParametro.getPorcGastosAdmin());
        response.setPorcInteresTotal(plazoParametro.getPorcInteresTotal());
        response.setCat(plazoParametro.getCat());
        response.setNumMaxRefrendos(plazoParametro.getNumMaxRefrendos());
        response.setPorcPrestamoSAvaluo(plazoParametro.getPorcPrestamoSAvaluo());
        response.setUsaAvaluoReal(plazoParametro.getUsaAvaluoReal());
        response.setPorcPrestamoSAvaluoReal(plazoParametro.getPorcPrestamoSAvaluoReal());
        response.setCobrarReposicionContrato(plazoParametro.getCobrarReposicionContrato());
        response.setReposicionEsPorcentaje(plazoParametro.getReposicionEsPorcentaje());
        response.setPorcReposicion(plazoParametro.getPorcReposicion());
        response.setMontoReposicion(plazoParametro.getMontoReposicion());
        response.setComisionPorVentaPrenda(plazoParametro.getComisionPorVentaPrenda());
        response.setAplicarSancionPorPeriodo(plazoParametro.getAplicarSancionPorPeriodo());
        response.setDiasGraciaSinInteres(plazoParametro.getDiasGraciaSinInteres());
        response.setDiasAntesPaseVenta(plazoParametro.getDiasAntesPaseVenta());
        response.setImporteMinPrestamo(plazoParametro.getImporteMinPrestamo());
        response.setCreadoEn(plazoParametro.getCreadoEn());
        response.setActualizadoEn(plazoParametro.getActualizadoEn());

        return response;
    }

    /**
     * Convierte un PlazoParametroRequest a PlazoParametro
     */
    public PlazoParametro toPlazoParametro(PlazoParametroRequest request) {
        if (request == null) {
            return null;
        }

        PlazoParametro plazoParametro = new PlazoParametro();
        plazoParametro.setPlazoId(request.getPlazoId());
        plazoParametro.setTipoPrendaId(request.getTipoPrendaId());
        plazoParametro.setPorcInteres(request.getPorcInteres() != null ? request.getPorcInteres() : BigDecimal.ZERO);
        plazoParametro.setPorcAlmacen(request.getPorcAlmacen() != null ? request.getPorcAlmacen() : BigDecimal.ZERO);
        plazoParametro.setPorcGastosAdmin(request.getPorcGastosAdmin() != null ? request.getPorcGastosAdmin() : BigDecimal.ZERO);
        plazoParametro.setPorcInteresTotal(request.getPorcInteresTotal() != null ? request.getPorcInteresTotal() : BigDecimal.ZERO);
        plazoParametro.setCat(request.getCat() != null ? request.getCat() : BigDecimal.ZERO);
        plazoParametro.setNumMaxRefrendos(request.getNumMaxRefrendos() != null ? request.getNumMaxRefrendos() : 0);
        plazoParametro.setPorcPrestamoSAvaluo(request.getPorcPrestamoSAvaluo() != null ? request.getPorcPrestamoSAvaluo() : BigDecimal.ZERO);
        plazoParametro.setUsaAvaluoReal(request.getUsaAvaluoReal() != null ? request.getUsaAvaluoReal() : false);
        plazoParametro.setPorcPrestamoSAvaluoReal(request.getPorcPrestamoSAvaluoReal() != null ? request.getPorcPrestamoSAvaluoReal() : BigDecimal.ZERO);
        plazoParametro.setCobrarReposicionContrato(request.getCobrarReposicionContrato() != null ? request.getCobrarReposicionContrato() : false);
        plazoParametro.setReposicionEsPorcentaje(request.getReposicionEsPorcentaje() != null ? request.getReposicionEsPorcentaje() : false);
        plazoParametro.setPorcReposicion(request.getPorcReposicion() != null ? request.getPorcReposicion() : BigDecimal.ZERO);
        plazoParametro.setMontoReposicion(request.getMontoReposicion() != null ? request.getMontoReposicion() : BigDecimal.ZERO);
        plazoParametro.setComisionPorVentaPrenda(request.getComisionPorVentaPrenda() != null ? request.getComisionPorVentaPrenda() : BigDecimal.ZERO);
        plazoParametro.setAplicarSancionPorPeriodo(request.getAplicarSancionPorPeriodo() != null ? request.getAplicarSancionPorPeriodo() : false);
        plazoParametro.setDiasGraciaSinInteres(request.getDiasGraciaSinInteres() != null ? request.getDiasGraciaSinInteres() : 0);
        plazoParametro.setDiasAntesPaseVenta(request.getDiasAntesPaseVenta() != null ? request.getDiasAntesPaseVenta() : 0);
        plazoParametro.setImporteMinPrestamo(request.getImporteMinPrestamo() != null ? request.getImporteMinPrestamo() : BigDecimal.ZERO);

        return plazoParametro;
    }

}

