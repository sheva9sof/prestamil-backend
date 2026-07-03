package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.PlazoHechuraAlhajaMapper;
import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.mapper.PlazoParametroMapper;
import com.ignis.prestamil.model.OroTablaPrestamo;
import com.ignis.prestamil.model.OroTablaPrestamoId;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.PrecioOro;
import com.ignis.prestamil.repository.OroTablaPrestamoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.PrecioOroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlazoServiceTest {

    @Mock
    PlazoRepository repository;

    @Mock
    PlazoMapper plazoMapper;

    @Mock
    TipoPrendaService tipoPrendaService;

    @Mock
    PlazoParametroRepository plazoParametroRepository;

    @Mock
    PlazoParametroMapper plazoParametroMapper;

    @Mock
    PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;

    @Mock
    PlazoHechuraAlhajaMapper plazoHechuraAlhajaMapper;

    @Mock
    PrecioOroRepository precioOroRepository;

    @Mock
    OroTablaPrestamoRepository oroTablaPrestamoRepository;

    PlazoService plazoService;

    @BeforeEach
    void setUp() {
        plazoService = new PlazoService(
                repository,
                plazoMapper,
                tipoPrendaService,
                plazoParametroRepository,
                plazoParametroMapper,
                plazoHechuraAlhajaRepository,
                plazoHechuraAlhajaMapper,
                precioOroRepository,
                oroTablaPrestamoRepository
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PlazoHechuraAlhaja buildFila(Integer idPlazo, Integer sucursalId, Integer kilataje,
                                          String hechura, BigDecimal porcAumento) {
        PlazoHechuraAlhaja fila = new PlazoHechuraAlhaja();
        fila.setId(new PlazoHechuraAlhajaId(idPlazo, sucursalId, kilataje, hechura));
        fila.setTablaPrestamoId(1);
        fila.setPorcAumento(porcAumento);
        return fila;
    }

    private OroTablaPrestamo buildCelda(Integer sucursalId, Integer kilataje, String hechura, BigDecimal porcPrestamo) {
        OroTablaPrestamo celda = new OroTablaPrestamo();
        celda.setId(new OroTablaPrestamoId(sucursalId, kilataje, hechura));
        celda.setPorcPrestamo(porcPrestamo);
        return celda;
    }

    private PrecioOro buildPrecioOro(Integer baseKilataje, BigDecimal factorFundir,
                                      BigDecimal factorNormal, BigDecimal factorEspecial) {
        PrecioOro precio = new PrecioOro();
        precio.setBaseKilataje(baseKilataje);
        precio.setFactorFundir(factorFundir);
        precio.setFactorNormal(factorNormal);
        precio.setFactorEspecial(factorEspecial);
        return precio;
    }

    // -----------------------------------------------------------------------
    // actualizarTodosPrecios tests
    // -----------------------------------------------------------------------

    @Test
    void actualizarTodosPrecios_21K_Normal_coincideConCOCAE() {
        // Given
        PlazoHechuraAlhaja fila21N = buildFila(1, 1, 21, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila21N));

        OroTablaPrestamo celda21N = buildCelda(1, 21, "N", new BigDecimal("63.4400"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21N));

        PrecioOro precioOroConBase21 = buildPrecioOro(21, null, null, null);
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precioOroConBase21));

        // When
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("1679.50"));

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("1065.4748"))).isEqualTo(0);
        assertThat(resultado.getPrecioPrestamo().compareTo(new BigDecimal("1172.0223"))).isEqualTo(0);
        // ORO-02: porcAumento propio de la celda no se sobreescribe durante el recalculo
        assertThat(resultado.getPorcAumento().compareTo(new BigDecimal("10.0000"))).isEqualTo(0);
    }

    @Test
    void actualizarTodosPrecios_ignoraFactoresHechuraGlobales() {
        // Given — mismo arrange que el test anterior, pero con factores de hechura globales
        // no default (50/100/150). actualizarTodosPrecios ya no los lee: el resultado debe
        // ser identico al del test 21K/Normal.
        PlazoHechuraAlhaja fila21N = buildFila(1, 1, 21, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila21N));

        OroTablaPrestamo celda21N = buildCelda(1, 21, "N", new BigDecimal("63.4400"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21N));

        PrecioOro precioOroConFactoresNoDefault = buildPrecioOro(21,
                new BigDecimal("50.0000"), new BigDecimal("100.0000"), new BigDecimal("150.0000"));
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precioOroConFactoresNoDefault));

        // When
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("1679.50"));

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("1065.4748"))).isEqualTo(0);
        assertThat(resultado.getPrecioPrestamo().compareTo(new BigDecimal("1172.0223"))).isEqualTo(0);
        assertThat(resultado.getPorcAumento().compareTo(new BigDecimal("10.0000"))).isEqualTo(0);
    }

    @Test
    void actualizarTodosPrecios_celdaSinPorcPrestamo_lanzaResourceNotFoundException() {
        // Given
        PlazoHechuraAlhaja fila14F = buildFila(1, 1, 14, "F", new BigDecimal("7.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila14F));

        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of());

        PrecioOro precioOroConBase21 = buildPrecioOro(21, null, null, null);
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precioOroConBase21));

        // When / Then
        assertThrows(ResourceNotFoundException.class,
                () -> plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("1679.50")));
    }

    @Test
    void actualizarTodosPrecios_registrosVacios_lanzaResourceNotFoundException() {
        // Given
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of());

        // When / Then
        assertThrows(ResourceNotFoundException.class,
                () -> plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("1679.50")));
        verify(plazoHechuraAlhajaRepository, never()).saveAll(any());
    }
}
