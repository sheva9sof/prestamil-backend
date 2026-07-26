package com.ignis.prestamil.service;

import com.ignis.prestamil.model.OroTablaPrestamo;
import com.ignis.prestamil.model.OroTablaPrestamoId;
import com.ignis.prestamil.model.PrecioOro;
import com.ignis.prestamil.repository.OroTablaPrestamoRepository;
import com.ignis.prestamil.repository.PrecioOroRepository;
import com.ignis.prestamil.request.OroCeldaUpdateRequest;
import com.ignis.prestamil.response.OroCeldaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura del factor de ajuste por hechura aplicado en la pantalla de referencia
 * "Configuracion del Oro" (OroTablaPrestamoService.toOroCeldaResponse). Quick task 260726-lin.
 */
@ExtendWith(MockitoExtension.class)
class OroTablaPrestamoServiceTest {

    @Mock
    OroTablaPrestamoRepository oroTablaPrestamoRepository;

    @Mock
    PrecioOroRepository precioOroRepository;

    @Mock
    PlazoService plazoService;

    OroTablaPrestamoService oroTablaPrestamoService;

    @BeforeEach
    void setUp() {
        oroTablaPrestamoService = new OroTablaPrestamoService(
                oroTablaPrestamoRepository,
                precioOroRepository,
                plazoService
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OroTablaPrestamo buildCelda(Integer sucursalId, Integer kilataje, String hechura, BigDecimal porcPrestamo) {
        OroTablaPrestamo celda = new OroTablaPrestamo();
        celda.setId(new OroTablaPrestamoId(sucursalId, kilataje, hechura));
        celda.setPorcPrestamo(porcPrestamo);
        return celda;
    }

    private PrecioOro buildPrecioOro(String precioGramo24k, Integer baseKilataje,
                                      String factorF, String factorN, String factorE) {
        PrecioOro precio = new PrecioOro();
        precio.setPrecioGramo24k(new BigDecimal(precioGramo24k));
        precio.setBaseKilataje(baseKilataje);
        precio.setFactorFundir(new BigDecimal(factorF));
        precio.setFactorNormal(new BigDecimal(factorN));
        precio.setFactorEspecial(new BigDecimal(factorE));
        return precio;
    }

    // -----------------------------------------------------------------------
    // getTabla tests
    // -----------------------------------------------------------------------

    @Test
    void getTabla_factorNeutro100_precioPrestamoIdenticoAlCalculoSinFactor() {
        // Given: precioGramo24k=2400.0000, baseKilataje=24 -> precioPorKilatePuro=100 (numeros redondos)
        PrecioOro precio = buildPrecioOro("2400.0000", 24, "100.0000", "100.0000", "100.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        OroTablaPrestamo celda21N = buildCelda(1, 21, "N", new BigDecimal("63.4400"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21N));

        // When
        List<OroCeldaResponse> tabla = oroTablaPrestamoService.getTabla(1);

        // Then: no-regresion -- mismo resultado que sin factor (factor neutro)
        OroCeldaResponse r = tabla.get(0);
        assertThat(r.getPrecioAvaluo().compareTo(new BigDecimal("2100.0000"))).isEqualTo(0);
        assertThat(r.getPrecioPrestamo().compareTo(new BigDecimal("1332.2400"))).isEqualTo(0);
    }

    @Test
    void getTabla_factorFundir90_reducePrecioPrestamoDeLaCeldaFundir() {
        // Given
        PrecioOro precio = buildPrecioOro("2400.0000", 24, "90.0000", "100.0000", "100.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        OroTablaPrestamo celda21F = buildCelda(1, 21, "F", new BigDecimal("62.6700"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21F));

        // When
        List<OroCeldaResponse> tabla = oroTablaPrestamoService.getTabla(1);

        // Then: 2100.0000 x 0.6267 x 0.90 = 1184.4630
        OroCeldaResponse r = tabla.get(0);
        assertThat(r.getPrecioPrestamo().compareTo(new BigDecimal("1184.4630"))).isEqualTo(0);
    }

    @Test
    void getTabla_seleccionaFactorPorHechura() {
        // Given: misma celda (14K) con el mismo porcPrestamo en las 3 hechuras, factores distintos
        PrecioOro precio = buildPrecioOro("2400.0000", 24, "90.0000", "100.0000", "110.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        OroTablaPrestamo celda14F = buildCelda(1, 14, "F", new BigDecimal("50.0000"));
        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        OroTablaPrestamo celda14E = buildCelda(1, 14, "E", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14F, celda14N, celda14E));

        // When
        List<OroCeldaResponse> tabla = oroTablaPrestamoService.getTabla(1);
        OroCeldaResponse f = tabla.stream().filter(c -> "F".equals(c.getHechura())).findFirst().orElseThrow();
        OroCeldaResponse n = tabla.stream().filter(c -> "N".equals(c.getHechura())).findFirst().orElseThrow();
        OroCeldaResponse e = tabla.stream().filter(c -> "E".equals(c.getHechura())).findFirst().orElseThrow();

        // Then: 1400 x 0.5 x factor/100 -> F=630.0000, N=700.0000, E=770.0000
        assertThat(f.getPrecioPrestamo().compareTo(new BigDecimal("630.0000"))).isEqualTo(0);
        assertThat(n.getPrecioPrestamo().compareTo(new BigDecimal("700.0000"))).isEqualTo(0);
        assertThat(e.getPrecioPrestamo().compareTo(new BigDecimal("770.0000"))).isEqualTo(0);
        assertThat(f.getPrecioPrestamo().compareTo(n.getPrecioPrestamo())).isLessThan(0);
        assertThat(n.getPrecioPrestamo().compareTo(e.getPrecioPrestamo())).isLessThan(0);
    }

    @Test
    void getTabla_sinPrecioOroConfigurado_usaFactorNeutro() {
        // Given
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.empty());
        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("63.2700"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        // When
        List<OroCeldaResponse> tabla = oroTablaPrestamoService.getTabla(1);

        // Then: no lanza NPE; con precioGramo24k=0 (fallback), todo queda en cero
        OroCeldaResponse r = tabla.get(0);
        assertThat(r.getPrecioAvaluo().compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(r.getPrecioPrestamo().compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // actualizarCelda tests
    // -----------------------------------------------------------------------

    @Test
    void actualizarCelda_aplicaFactorEnLaRespuesta() {
        // Given
        OroTablaPrestamo existente = buildCelda(1, 14, "N", new BigDecimal("60.0000"));
        when(oroTablaPrestamoRepository.findById(new OroTablaPrestamoId(1, 14, "N")))
                .thenReturn(Optional.of(existente));
        when(oroTablaPrestamoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrecioOro precio = buildPrecioOro("2400.0000", 24, "100.0000", "110.0000", "100.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        OroCeldaUpdateRequest req = new OroCeldaUpdateRequest();
        req.setPorcPrestamo(new BigDecimal("63.2700"));

        // When
        OroCeldaResponse response = oroTablaPrestamoService.actualizarCelda(1, 14, "N", req);

        // Then: 1400 x 0.6327 x 1.10 = 974.3580, y se dispara la cascada del motor de plazos
        assertThat(response.getPrecioPrestamo().compareTo(new BigDecimal("974.3580"))).isEqualTo(0);
        verify(plazoService).recalcularPrecioBasePorTablaOro(1);
    }
}
