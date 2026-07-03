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
        response.setSucursalId(plazoParametro.getSucursalId());

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
        response.setPorcIncrementoAvaluo(plazoParametro.getPorcIncrementoAvaluo());
        response.setCobrarReposicionContrato(plazoParametro.getCobrarReposicionContrato());
        response.setReposicionEsPorcentaje(plazoParametro.getReposicionEsPorcentaje());
        response.setPorcReposicion(plazoParametro.getPorcReposicion());
        response.setMontoReposicion(plazoParametro.getMontoReposicion());
        response.setComisionPorVentaPrenda(plazoParametro.getComisionPorVentaPrenda());
        response.setAplicarSancionPorPeriodo(plazoParametro.getAplicarSancionPorPeriodo());
        response.setPorcSancionSemanal(plazoParametro.getPorcSancionSemanal());
        response.setLey925(plazoParametro.getLey925());
        response.setLey725(plazoParametro.getLey725());
        response.setPrecioGramoPlata(plazoParametro.getPrecioGramoPlata());
        response.setDiasGraciaSinInteres(plazoParametro.getDiasGraciaSinInteres());
        response.setDiasAntesPaseVenta(plazoParametro.getDiasAntesPaseVenta());
        response.setImporteMinPrestamo(plazoParametro.getImporteMinPrestamo());
        response.setCreadoEn(plazoParametro.getCreadoEn());
        response.setActualizadoEn(plazoParametro.getActualizadoEn());

        return response;
    }

    /**
     * Actualiza los campos editables de un PlazoParametro existente desde un request.
     * No modifica la clave compuesta (plazoId, tipoPrendaId, sucursalId).
     *
     * @param entity  entidad a actualizar
     * @param request DTO con los nuevos valores
     */
    public void actualizarDesdeRequest(PlazoParametro entity, PlazoParametroRequest request) {
        if (request == null) return;
        if (request.getPorcInteres() != null) entity.setPorcInteres(request.getPorcInteres());
        if (request.getPorcAlmacen() != null) entity.setPorcAlmacen(request.getPorcAlmacen());
        if (request.getPorcGastosAdmin() != null) entity.setPorcGastosAdmin(request.getPorcGastosAdmin());
        if (request.getPorcInteresTotal() != null) entity.setPorcInteresTotal(request.getPorcInteresTotal());
        if (request.getCat() != null) entity.setCat(request.getCat());
        if (request.getNumMaxRefrendos() != null) entity.setNumMaxRefrendos(request.getNumMaxRefrendos());
        if (request.getPorcPrestamoSAvaluo() != null) entity.setPorcPrestamoSAvaluo(request.getPorcPrestamoSAvaluo());
        if (request.getUsaAvaluoReal() != null) entity.setUsaAvaluoReal(request.getUsaAvaluoReal());
        if (request.getPorcIncrementoAvaluo() != null) entity.setPorcIncrementoAvaluo(request.getPorcIncrementoAvaluo());
        if (request.getCobrarReposicionContrato() != null) entity.setCobrarReposicionContrato(request.getCobrarReposicionContrato());
        if (request.getReposicionEsPorcentaje() != null) entity.setReposicionEsPorcentaje(request.getReposicionEsPorcentaje());
        if (request.getPorcReposicion() != null) entity.setPorcReposicion(request.getPorcReposicion());
        if (request.getMontoReposicion() != null) entity.setMontoReposicion(request.getMontoReposicion());
        if (request.getComisionPorVentaPrenda() != null) entity.setComisionPorVentaPrenda(request.getComisionPorVentaPrenda());
        if (request.getAplicarSancionPorPeriodo() != null) entity.setAplicarSancionPorPeriodo(request.getAplicarSancionPorPeriodo());
        if (request.getPorcSancionSemanal() != null) entity.setPorcSancionSemanal(request.getPorcSancionSemanal());
        if (request.getLey925() != null) entity.setLey925(request.getLey925());
        if (request.getLey725() != null) entity.setLey725(request.getLey725());
        if (request.getPrecioGramoPlata() != null) entity.setPrecioGramoPlata(request.getPrecioGramoPlata());
        if (request.getDiasGraciaSinInteres() != null) entity.setDiasGraciaSinInteres(request.getDiasGraciaSinInteres());
        if (request.getDiasAntesPaseVenta() != null) entity.setDiasAntesPaseVenta(request.getDiasAntesPaseVenta());
        if (request.getImporteMinPrestamo() != null) entity.setImporteMinPrestamo(request.getImporteMinPrestamo());
    }

    /**
     * Convierte un PlazoParametroRequest a PlazoParametro
     */
    public PlazoParametro toPlazoParametro(PlazoParametroRequest request) {
        if (request == null) {
            return null;
        }

        PlazoParametro plazoParametro = new PlazoParametro();
        plazoParametro.setPorcInteres(request.getPorcInteres() != null ? request.getPorcInteres() : BigDecimal.ZERO);
        plazoParametro.setPorcAlmacen(request.getPorcAlmacen() != null ? request.getPorcAlmacen() : BigDecimal.ZERO);
        plazoParametro.setPorcGastosAdmin(request.getPorcGastosAdmin() != null ? request.getPorcGastosAdmin() : BigDecimal.ZERO);
        plazoParametro.setPorcInteresTotal(request.getPorcInteresTotal() != null ? request.getPorcInteresTotal() : BigDecimal.ZERO);
        plazoParametro.setCat(request.getCat() != null ? request.getCat() : BigDecimal.ZERO);
        plazoParametro.setNumMaxRefrendos(request.getNumMaxRefrendos() != null ? request.getNumMaxRefrendos() : 0);
        plazoParametro.setPorcPrestamoSAvaluo(request.getPorcPrestamoSAvaluo() != null ? request.getPorcPrestamoSAvaluo() : BigDecimal.ZERO);
        plazoParametro.setUsaAvaluoReal(request.getUsaAvaluoReal() != null ? request.getUsaAvaluoReal() : false);
        plazoParametro.setPorcIncrementoAvaluo(request.getPorcIncrementoAvaluo() != null ? request.getPorcIncrementoAvaluo() : new BigDecimal("50.0000"));
        plazoParametro.setCobrarReposicionContrato(request.getCobrarReposicionContrato() != null ? request.getCobrarReposicionContrato() : false);
        plazoParametro.setReposicionEsPorcentaje(request.getReposicionEsPorcentaje() != null ? request.getReposicionEsPorcentaje() : false);
        plazoParametro.setPorcReposicion(request.getPorcReposicion() != null ? request.getPorcReposicion() : BigDecimal.ZERO);
        plazoParametro.setMontoReposicion(request.getMontoReposicion() != null ? request.getMontoReposicion() : BigDecimal.ZERO);
        plazoParametro.setComisionPorVentaPrenda(request.getComisionPorVentaPrenda() != null ? request.getComisionPorVentaPrenda() : BigDecimal.ZERO);
        plazoParametro.setAplicarSancionPorPeriodo(request.getAplicarSancionPorPeriodo() != null ? request.getAplicarSancionPorPeriodo() : false);
        plazoParametro.setPorcSancionSemanal(request.getPorcSancionSemanal() != null ? request.getPorcSancionSemanal() : new BigDecimal("2.0000"));
        plazoParametro.setLey925(request.getLey925() != null ? request.getLey925() : BigDecimal.ZERO);
        plazoParametro.setLey725(request.getLey725() != null ? request.getLey725() : BigDecimal.ZERO);
        plazoParametro.setPrecioGramoPlata(request.getPrecioGramoPlata() != null ? request.getPrecioGramoPlata() : BigDecimal.ZERO);
        plazoParametro.setDiasGraciaSinInteres(request.getDiasGraciaSinInteres() != null ? request.getDiasGraciaSinInteres() : 0);
        plazoParametro.setDiasAntesPaseVenta(request.getDiasAntesPaseVenta() != null ? request.getDiasAntesPaseVenta() : 0);
        plazoParametro.setImporteMinPrestamo(request.getImporteMinPrestamo() != null ? request.getImporteMinPrestamo() : BigDecimal.ZERO);

        return plazoParametro;
    }

}

