package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.OroTablaPrestamo;
import com.ignis.prestamil.model.OroTablaPrestamoId;
import com.ignis.prestamil.model.PrecioOro;
import com.ignis.prestamil.repository.OroTablaPrestamoRepository;
import com.ignis.prestamil.repository.PrecioOroRepository;
import com.ignis.prestamil.request.OroCeldaUpdateRequest;
import com.ignis.prestamil.response.OroCeldaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de la pantalla "Configuracion del Oro": expone las 24 celdas de
 * {@code oro_tabla_prestamo} (8 kilates x 3 hechuras) con sus precios calculados
 * y permite editar el %Prestamo de una celda, disparando el recalculo en cascada
 * de {@link PlazoService#recalcularPrecioBasePorTablaOro(Integer)} (ORO-05, ORO-06, ORO-07).
 *
 * Formula completa del "Precio Prestamo (referencia)" (D-A, quick task 260726-lin):
 * {@code precioPrestamo = precioAvaluo x %Prestamo/100 x factorDeHechura(hechura)/100}.
 * El mismo factor de ajuste por hechura (Fundir/Normal/Especial, configurable por
 * sucursal en {@code precio_oro}) se aplica en {@code PlazoService.recalcularRegistros}
 * al derivar {@code PlazoHechuraAlhaja.precioBase} — para la misma celda kilataje/hechura,
 * ambos numeros coinciden (ORO-09).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OroTablaPrestamoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final OroTablaPrestamoRepository oroTablaPrestamoRepository;
    private final PrecioOroRepository precioOroRepository;
    private final PlazoService plazoService;

    /**
     * Lista las 24 celdas de %Prestamo de la sucursal con su precioAvaluo y precioPrestamo
     * de referencia ya calculados: {@code precioPrestamo = precioAvaluo x %Prestamo/100
     * x factorDeHechura(hechura)/100}. Este numero coincide con {@code PlazoHechuraAlhaja.precioBase}
     * para la misma celda kilataje/hechura, porque ambos motores comparten {@link PrecioOro#factorDeHechura}.
     *
     * @param sucursalId identificador de la sucursal
     * @return lista de celdas ordenada por kilataje ascendente y luego hechura (F, N, E)
     */
    @Transactional(readOnly = true)
    public List<OroCeldaResponse> getTabla(Integer sucursalId) {
        PrecioOro precio = precioOroRepository.findBySucursalId(sucursalId).orElse(null);

        List<OroTablaPrestamo> celdas = oroTablaPrestamoRepository.findByIdSucursalId(sucursalId);

        return celdas.stream()
                .map(celda -> toOroCeldaResponse(celda, precio))
                .sorted(Comparator.comparing(OroCeldaResponse::getKilataje)
                        .thenComparing(r -> ordenHechura(r.getHechura())))
                .toList();
    }

    /**
     * Actualiza el %Prestamo de una celda (kilataje/hechura) y dispara el recalculo en
     * cascada de {@code precioBase}/{@code precioPrestamo} en todos los {@code PlazoHechuraAlhaja}
     * de la sucursal, preservando el {@code porcAumento} propio de cada plazo (ORO-06).
     *
     * @param sucursalId identificador de la sucursal
     * @param kilataje   kilataje de la celda a editar
     * @param hechura    hechura de la celda a editar ("F", "N" o "E")
     * @param request    nuevo %Prestamo a persistir
     * @return OroCeldaResponse con el %Prestamo y precios ya recalculados
     * @throws BadRequestException       si se intenta editar la celda de 24K (no prendable, ORO-07/D-12)
     * @throws ResourceNotFoundException si la celda no existe para la sucursal
     */
    public OroCeldaResponse actualizarCelda(Integer sucursalId, Integer kilataje, String hechura,
                                             OroCeldaUpdateRequest request) {
        if (kilataje == 24) {
            throw new BadRequestException("El oro de 24K no es prendable; su %Prestamo no es editable");
        }

        OroTablaPrestamoId id = new OroTablaPrestamoId(sucursalId, kilataje, hechura);
        OroTablaPrestamo entity = oroTablaPrestamoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe la celda oro_tabla_prestamo: sucursal=" + sucursalId
                                + ", kilataje=" + kilataje + ", hechura=" + hechura));

        entity.setPorcPrestamo(request.getPorcPrestamo());
        entity.setActualizadoEn(LocalDateTime.now());
        OroTablaPrestamo guardada = oroTablaPrestamoRepository.save(entity);

        // Recalcula precioBase/precioPrestamo de todos los plazos existentes con la nueva celda
        plazoService.recalcularPrecioBasePorTablaOro(sucursalId);

        PrecioOro precio = precioOroRepository.findBySucursalId(sucursalId).orElse(null);

        return toOroCeldaResponse(guardada, precio);
    }

    private OroCeldaResponse toOroCeldaResponse(OroTablaPrestamo celda, PrecioOro precio) {
        int kilataje = celda.getId().getKilataje();
        String hechura = celda.getId().getHechura();
        BigDecimal porcPrestamo = celda.getPorcPrestamo();

        BigDecimal precioGramo24k = precio != null ? precio.getPrecioGramo24k() : BigDecimal.ZERO;
        int baseKilataje = precio != null && precio.getBaseKilataje() != null ? precio.getBaseKilataje() : 24;

        BigDecimal precioAvaluo = precioGramo24k
                .divide(new BigDecimal(baseKilataje), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(kilataje))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal precioPrestamo = precioAvaluo
                .multiply(porcPrestamo.divide(CIEN, 10, RoundingMode.HALF_UP))
                .multiply(PrecioOro.factorDeHechura(precio, hechura).divide(CIEN, 10, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);

        OroCeldaResponse response = new OroCeldaResponse();
        response.setKilataje(kilataje);
        response.setHechura(hechura);
        response.setHechuraDescripcion(descripcionHechura(hechura));
        response.setPrecioAvaluo(precioAvaluo);
        response.setPorcPrestamo(porcPrestamo);
        response.setPrecioPrestamo(precioPrestamo);
        response.setEditable(kilataje != 24);
        return response;
    }

    private static String descripcionHechura(String hechura) {
        switch (hechura) {
            case "F": return "Fundir";
            case "N": return "Normal";
            case "E": return "Especial";
            default: return hechura;
        }
    }

    private static int ordenHechura(String hechura) {
        switch (hechura) {
            case "F": return 0;
            case "N": return 1;
            case "E": return 2;
            default: return 3;
        }
    }
}
