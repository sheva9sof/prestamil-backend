package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.PlazoHechuraAlhajaMapper;
import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.mapper.PlazoParametroMapper;
import com.ignis.prestamil.model.OroTablaPrestamo;
import com.ignis.prestamil.model.OroTablaPrestamoId;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PrecioOro;
import com.ignis.prestamil.repository.OroTablaPrestamoRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.PrecioOroRepository;
import com.ignis.prestamil.request.PrecioOroRequest;
import com.ignis.prestamil.response.PrecioOroResponse;
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
import static org.mockito.Mockito.inOrder;
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

    @Mock
    ContratoRepository contratoRepository;

    @Mock
    com.ignis.prestamil.repository.UsuarioRepository usuarioRepository;

    @Mock
    com.ignis.prestamil.service.calculo.ParametrosSistemaCache parametrosSistemaCache;

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
                oroTablaPrestamoRepository,
                contratoRepository,
                usuarioRepository,
                parametrosSistemaCache
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

    private PrecioOro buildPrecioOro(Integer baseKilataje) {
        return buildPrecioOro(baseKilataje, "100.0000", "100.0000", "100.0000");
    }

    private PrecioOro buildPrecioOro(Integer baseKilataje, String factorF, String factorN, String factorE) {
        PrecioOro precio = new PrecioOro();
        precio.setBaseKilataje(baseKilataje);
        precio.setFactorFundir(new BigDecimal(factorF));
        precio.setFactorNormal(new BigDecimal(factorN));
        precio.setFactorEspecial(new BigDecimal(factorE));
        return precio;
    }

    // -----------------------------------------------------------------------
    // actualizarTodosPrecios tests
    // -----------------------------------------------------------------------

    @Test
    void eliminarPlazo_sinContratos_eliminaConfiguracionDependiente() {
        Plazo plazo = new Plazo();
        plazo.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(plazo));
        when(contratoRepository.existsByPlazoId(7L)).thenReturn(false);

        plazoService.eliminarPlazo(7L);

        org.mockito.InOrder orden = inOrder(
                plazoParametroRepository, plazoHechuraAlhajaRepository, repository);
        orden.verify(plazoParametroRepository).deleteByPlazoId(7L);
        orden.verify(plazoParametroRepository).flush();
        orden.verify(plazoHechuraAlhajaRepository).deleteByIdIdPlazo(7);
        orden.verify(plazoHechuraAlhajaRepository).flush();
        orden.verify(repository).saveAndFlush(plazo);
        orden.verify(repository).delete(plazo);
    }

    @Test
    void eliminarPlazo_conContratos_rechazaEliminacion() {
        Plazo plazo = new Plazo();
        plazo.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(plazo));
        when(contratoRepository.existsByPlazoId(7L)).thenReturn(true);

        assertThrows(com.ignis.prestamil.exception.BadRequestException.class,
                () -> plazoService.eliminarPlazo(7L));

        verify(plazoParametroRepository, never()).deleteByPlazoId(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void actualizarTodosPrecios_21K_Normal_coincideConCOCAE() {
        // Given
        PlazoHechuraAlhaja fila21N = buildFila(1, 1, 21, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila21N));

        OroTablaPrestamo celda21N = buildCelda(1, 21, "N", new BigDecimal("63.4400"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda21N));

        PrecioOro precioOroConBase21 = buildPrecioOro(21);
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
    void calcularPrecioAvaluoGramo_deshaceAumentoYPorcPrestamo_devuelveValorDelOro() {
        // Misma celda 21K/N del caso COCAE de arriba: precio del gramo 1679.50 (base 21K, factor
        // neutro) -> precioBase 1065.4748 -> precioPrestamo 1172.0223 con 10% de aumento. Deshacer
        // el aumento y el 63.44% de préstamo regresa al valor del oro: 1679.50 por gramo.
        PlazoHechuraAlhaja fila21N = buildFila(1, 1, 21, "N", new BigDecimal("10.0000"));
        fila21N.setPrecioPrestamo(new BigDecimal("1172.0223"));
        when(oroTablaPrestamoRepository.findById(new OroTablaPrestamoId(1, 21, "N")))
                .thenReturn(Optional.of(buildCelda(1, 21, "N", new BigDecimal("63.4400"))));

        assertThat(plazoService.calcularPrecioAvaluoGramo(fila21N)).isEqualByComparingTo("1679.5000");
    }

    @Test
    void calcularPrecioAvaluoGramo_celdaSinPorcPrestamo_lanzaResourceNotFoundException() {
        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("6.0000"));
        fila14N.setPrecioPrestamo(new BigDecimal("500.0000"));
        when(oroTablaPrestamoRepository.findById(new OroTablaPrestamoId(1, 14, "N")))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> plazoService.calcularPrecioAvaluoGramo(fila14N));
    }

    @Test
    void actualizarTodosPrecios_celdaSinPorcPrestamo_lanzaResourceNotFoundException() {
        // Given
        PlazoHechuraAlhaja fila14F = buildFila(1, 1, 14, "F", new BigDecimal("7.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila14F));

        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of());

        PrecioOro precioOroConBase21 = buildPrecioOro(21);
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

    // -----------------------------------------------------------------------
    // Factor de ajuste por hechura (quick task 260726-lin, ORO-09)
    // Escenario base compartido: precioGramoBase="2400.0000", baseKilataje=24
    // -> precioPorKilatePuro=100; celda 14K/"N" porcPrestamo="50.0000"
    // -> precioAvaluo=1400.0000, precioBase sin factor=700.0000; porcAumento="10.0000"
    // -----------------------------------------------------------------------

    @Test
    void actualizarTodosPrecios_factorNeutro100_precioBaseSinCambio() {
        // Given
        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila14N));

        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        PrecioOro precio = buildPrecioOro(24, "100.0000", "100.0000", "100.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        // When
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("2400.0000"));

        // Then: no-regresion explicita con factor neutro
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("700.0000"))).isEqualTo(0);
        assertThat(resultado.getPrecioPrestamo().compareTo(new BigDecimal("770.0000"))).isEqualTo(0);
    }

    @Test
    void actualizarTodosPrecios_factorNormal90_reducePrecioBaseProporcionalmente() {
        // Given
        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila14N));

        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        PrecioOro precio = buildPrecioOro(24, "100.0000", "90.0000", "100.0000");
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        // When
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("2400.0000"));

        // Then: 700 x 0.90 = 630.0000; precioPrestamo = 630 x 1.10 = 693.0000
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("630.0000"))).isEqualTo(0);
        assertThat(resultado.getPrecioPrestamo().compareTo(new BigDecimal("693.0000"))).isEqualTo(0);
        // D-10: el factor no contamina el porcAumento propio del plazo
        assertThat(resultado.getPorcAumento().compareTo(new BigDecimal("10.0000"))).isEqualTo(0);
    }

    @Test
    void actualizarTodosPrecios_sinPrecioOroConfigurado_usaFactorNeutro() {
        // Given
        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(1, 1)).thenReturn(List.of(fila14N));

        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.empty());

        // When: sin PrecioOro configurado, baseKilataje cae al default 24 y el factor es neutro
        plazoService.actualizarTodosPrecios(1, 1, new BigDecimal("2400.0000"));

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);

        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("700.0000"))).isEqualTo(0);
    }

    @Test
    void recalcularTodasLasTablas_aplicaFactorDelRequestEnElMismoRecalculo() {
        // Given: PrecioOro vigente con factorNormal="100.0000"
        PrecioOro precioVigente = buildPrecioOro(24, "100.0000", "100.0000", "100.0000");
        precioVigente.setSucursalId(1);
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precioVigente));
        when(precioOroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdSucursalId(1)).thenReturn(List.of(fila14N));

        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        // Request: precioGramoBase="2400.0000" y factorNormal="90.0000" (fundir/especial null -> conservan vigente)
        PrecioOroRequest request = new PrecioOroRequest();
        request.setPrecioGramoBase(new BigDecimal("2400.0000"));
        request.setFactorNormal(new BigDecimal("90.0000"));

        // When
        PrecioOroResponse response = plazoService.recalcularTodasLasTablas(1, request, "tester");

        // Then: el factor del request se aplico EN EL MISMO recalculo (700 x 0.90 = 630.0000)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);
        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("630.0000"))).isEqualTo(0);

        // Y el PrecioOro persistido refleja el upsert: factorNormal del request, los otros conservan el vigente
        ArgumentCaptor<PrecioOro> precioCaptor = ArgumentCaptor.forClass(PrecioOro.class);
        verify(precioOroRepository).save(precioCaptor.capture());
        PrecioOro guardado = precioCaptor.getValue();
        assertThat(guardado.getFactorNormal().compareTo(new BigDecimal("90.0000"))).isEqualTo(0);
        assertThat(guardado.getFactorFundir().compareTo(new BigDecimal("100.0000"))).isEqualTo(0);
        assertThat(guardado.getFactorEspecial().compareTo(new BigDecimal("100.0000"))).isEqualTo(0);

        assertThat(response.getFactorNormal().compareTo(new BigDecimal("90.0000"))).isEqualTo(0);
    }

    @Test
    void recalcularPrecioBasePorTablaOro_usaElFactorPersistido() {
        // Given
        PrecioOro precio = buildPrecioOro(24, "100.0000", "90.0000", "100.0000");
        precio.setPrecioGramo24k(new BigDecimal("2400.0000"));
        when(precioOroRepository.findBySucursalId(1)).thenReturn(Optional.of(precio));

        PlazoHechuraAlhaja fila14N = buildFila(1, 1, 14, "N", new BigDecimal("10.0000"));
        when(plazoHechuraAlhajaRepository.findByIdSucursalId(1)).thenReturn(List.of(fila14N));

        OroTablaPrestamo celda14N = buildCelda(1, 14, "N", new BigDecimal("50.0000"));
        when(oroTablaPrestamoRepository.findByIdSucursalId(1)).thenReturn(List.of(celda14N));

        // When
        plazoService.recalcularPrecioBasePorTablaOro(1);

        // Then: la cascada disparada desde OroTablaPrestamoService.actualizarCelda tambien lleva el factor
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlazoHechuraAlhaja>> captor = ArgumentCaptor.forClass(List.class);
        verify(plazoHechuraAlhajaRepository).saveAll(captor.capture());
        PlazoHechuraAlhaja resultado = captor.getValue().get(0);
        assertThat(resultado.getPrecioBase().compareTo(new BigDecimal("630.0000"))).isEqualTo(0);
    }

    // =========================================================================
    // D-14: porc_sancion_semanal obligatorio al guardar. Fila nueva sin el campo
    // en el request toma el default de Parametros Generales (id=19, 2%).
    // =========================================================================

    @Test
    void guardarParametro_filaNueva_sinSancionEnRequest_tomaDefaultDeParametrosSistema() {
        // Given: la pantalla de Plazos y Periodos manda el PATCH sin porcSancionSemanal
        // (campo no tocado por el usuario). La fila no existe todavia (nueva combinacion
        // plazo+tipoPrenda+sucursal). Antes del fix de D-14 esto grababa 0 en BD y
        // la tabla de amortizacion / PDF salian con sancion = 0% aunque el cobro siguiera
        // aplicando el 2%.
        com.ignis.prestamil.model.PlazoParametroId id =
                new com.ignis.prestamil.model.PlazoParametroId(99L, 1, 1);
        when(plazoParametroRepository.existsById(id)).thenReturn(false);
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.empty());
        when(parametrosSistemaCache.getPorcSancionSemanalDefault())
                .thenReturn(new BigDecimal("2"));
        when(plazoParametroRepository.save(any(com.ignis.prestamil.model.PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcInteres(new BigDecimal("1.55"));
        req.setPorcAlmacen(new BigDecimal("0.6"));
        // Importante: porcSancionSemanal queda en null (no lo envio la UI).

        // When
        plazoService.guardarParametro(99L, 1, 1, req, "cajero1");

        // Then: antes de delegar al mapper, el servicio inyecto el default en el request.
        // Verifica tanto que el mapper recibio el valor (vector principal) como que la
        // entidad guardada acabo con 2% (vector final que PDF/caja leen).
        ArgumentCaptor<com.ignis.prestamil.model.PlazoParametro> captor =
                ArgumentCaptor.forClass(com.ignis.prestamil.model.PlazoParametro.class);
        verify(plazoParametroMapper)
                .actualizarDesdeRequest(captor.capture(), org.mockito.ArgumentMatchers.argThat(r ->
                        r != null && r.getPorcSancionSemanal() != null
                                && r.getPorcSancionSemanal().compareTo(new BigDecimal("2")) == 0));
        verify(parametrosSistemaCache).getPorcSancionSemanalDefault();
    }

    @Test
    void guardarParametro_filaExistenteConCero_sinSancionEnRequest_seCorrigeADefault() {
        // D-14: filas existentes con porc_sancion_semanal=0 (dato legado) tambien se
        // auto-corrigen al default cuando el request viene sin el campo, para no dejar
        // 0% silenciosamente en la tabla y PDF.
        com.ignis.prestamil.model.PlazoParametroId id =
                new com.ignis.prestamil.model.PlazoParametroId(5L, 1, 1);
        com.ignis.prestamil.model.PlazoParametro existente = new com.ignis.prestamil.model.PlazoParametro();
        existente.setPlazoId(5L); existente.setTipoPrendaId(1); existente.setSucursalId(1);
        existente.setPorcSancionSemanal(BigDecimal.ZERO);

        when(plazoParametroRepository.existsById(id)).thenReturn(true);
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.of(existente));
        when(parametrosSistemaCache.getPorcSancionSemanalDefault())
                .thenReturn(new BigDecimal("2"));
        when(plazoParametroRepository.save(any(com.ignis.prestamil.model.PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcInteres(new BigDecimal("1.13"));

        plazoService.guardarParametro(5L, 1, 1, req, "cajero1");

        verify(plazoParametroMapper)
                .actualizarDesdeRequest(any(), org.mockito.ArgumentMatchers.argThat(r ->
                        r != null && r.getPorcSancionSemanal() != null
                                && r.getPorcSancionSemanal().compareTo(new BigDecimal("2")) == 0));
    }

    @Test
    void guardarParametro_filaExistenteConValor_sinSancionEnRequest_conservaValor() {
        // D-14: si la fila ya tiene un valor valido y el request no envia el campo, no
        // se sobreescribe (el default solo entra para filas nuevas o filas en 0).
        com.ignis.prestamil.model.PlazoParametroId id =
                new com.ignis.prestamil.model.PlazoParametroId(6L, 1, 1);
        com.ignis.prestamil.model.PlazoParametro existente = new com.ignis.prestamil.model.PlazoParametro();
        existente.setPlazoId(6L); existente.setTipoPrendaId(1); existente.setSucursalId(1);
        existente.setPorcSancionSemanal(new BigDecimal("3.5000"));

        when(plazoParametroRepository.existsById(id)).thenReturn(true);
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.of(existente));
        when(plazoParametroRepository.save(any(com.ignis.prestamil.model.PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcInteres(new BigDecimal("1.13"));

        plazoService.guardarParametro(6L, 1, 1, req, "cajero1");

        // El request sigue trayendo null -> mapper recibe el request tal cual (y como
        // en el mapper hay `if (request.getPorcSancionSemanal() != null)` el valor
        // previo se conserva).
        verify(plazoParametroMapper)
                .actualizarDesdeRequest(any(), org.mockito.ArgumentMatchers.argThat(r ->
                        r != null && r.getPorcSancionSemanal() == null));
        verify(parametrosSistemaCache, never()).getPorcSancionSemanalDefault();
    }

    // =========================================================================
    // D-07: validación de rango de porc_prestamo_s_avaluo
    // =========================================================================

    @Test
    void guardarParametro_porcPrestamoSAvaluoFueraDeRango_rechaza() {
        // D-07: capturar 101 (o cualquier valor > 100) es un error — probablemente la pantalla
        // confundió el campo. El mensaje orienta a dónde debía ir el valor.
        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcPrestamoSAvaluo(new BigDecimal("101"));

        com.ignis.prestamil.exception.BadRequestException ex = assertThrows(
                com.ignis.prestamil.exception.BadRequestException.class,
                () -> plazoService.guardarParametro(8L, 1, 1, req, "cajero1"));
        assertThat(ex.getMessage()).contains("entre 0 y 100");
        assertThat(ex.getMessage()).contains("101");
        assertThat(ex.getMessage()).contains("hechura");
    }

    @Test
    void guardarParametro_porcPrestamoSAvaluoNegativo_rechaza() {
        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcPrestamoSAvaluo(new BigDecimal("-5"));

        com.ignis.prestamil.exception.BadRequestException ex = assertThrows(
                com.ignis.prestamil.exception.BadRequestException.class,
                () -> plazoService.guardarParametro(8L, 1, 1, req, "cajero1"));
        assertThat(ex.getMessage()).contains("entre 0 y 100");
    }

    @Test
    void d08_guardarParametro_montoReposicion40Fijo_persisteYSeReleeConMismosValores() {
        // D-08: $40 fijo capturado en Plazos y Periodos debe persistirse y reaparecer al abrir
        // de nuevo la pantalla. Mapper real (no mock) para validar el camino de ida y vuelta.
        com.ignis.prestamil.mapper.PrendaMapper prendaMapperReal =
                org.mockito.Mockito.mock(com.ignis.prestamil.mapper.PrendaMapper.class);
        com.ignis.prestamil.mapper.PlazoParametroMapper mapperReal =
                new com.ignis.prestamil.mapper.PlazoParametroMapper(prendaMapperReal);
        PlazoService svc = new PlazoService(
                repository, plazoMapper, tipoPrendaService, plazoParametroRepository,
                mapperReal, plazoHechuraAlhajaRepository, plazoHechuraAlhajaMapper,
                precioOroRepository, oroTablaPrestamoRepository, contratoRepository,
                usuarioRepository, parametrosSistemaCache);

        com.ignis.prestamil.model.PlazoParametroId id =
                new com.ignis.prestamil.model.PlazoParametroId(8L, 1, 1);
        com.ignis.prestamil.model.PlazoParametro existente = new com.ignis.prestamil.model.PlazoParametro();
        existente.setPlazoId(8L); existente.setTipoPrendaId(1); existente.setSucursalId(1);
        when(plazoParametroRepository.existsById(id)).thenReturn(true);
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.of(existente));
        when(plazoParametroRepository.save(any(com.ignis.prestamil.model.PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setCobrarReposicionContrato(true);
        req.setReposicionEsPorcentaje(false);
        req.setMontoReposicion(new BigDecimal("40.00"));
        req.setPorcReposicion(BigDecimal.ZERO);
        req.setPorcSancionSemanal(new BigDecimal("2"));

        com.ignis.prestamil.response.PlazoParametroResponse guardado =
                svc.guardarParametro(8L, 1, 1, req, "cajero1");

        // Vector 1: la entidad persistida trae los tres campos de reposición
        assertThat(existente.getCobrarReposicionContrato()).isTrue();
        assertThat(existente.getReposicionEsPorcentaje()).isFalse();
        assertThat(existente.getMontoReposicion()).isEqualByComparingTo("40.00");
        assertThat(existente.getPorcReposicion()).isEqualByComparingTo("0");

        // Vector 2: al releer el response (lo que la pantalla recibe al refrescar) trae lo mismo
        assertThat(guardado.getCobrarReposicionContrato()).isTrue();
        assertThat(guardado.getReposicionEsPorcentaje()).isFalse();
        assertThat(guardado.getMontoReposicion()).isEqualByComparingTo("40.00");

        // Vector 3: getParametrosPlazo (ruta GET independiente que usa el load del form)
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.of(existente));
        com.ignis.prestamil.response.PlazoParametroResponse recargado =
                svc.getParametrosPlazo(8L, 1, 1);
        assertThat(recargado.getMontoReposicion()).isEqualByComparingTo("40.00");
        assertThat(recargado.getCobrarReposicionContrato()).isTrue();
        assertThat(recargado.getReposicionEsPorcentaje()).isFalse();
    }

    @Test
    void guardarParametro_porcPrestamoSAvaluoEnLimiteBajo_persisteSinError() {
        // Un 6% técnicamente válido (persiste) pero loguea advertencia. El plazo Principal usa
        // 0 ("sin tope"); el valor típico de alhajas es 50–90. 6 pasa la validación pero
        // es el valor que causó el bug — logeamos para que sea visible en operaciones.
        com.ignis.prestamil.model.PlazoParametroId id =
                new com.ignis.prestamil.model.PlazoParametroId(8L, 1, 1);
        com.ignis.prestamil.model.PlazoParametro existente = new com.ignis.prestamil.model.PlazoParametro();
        existente.setPlazoId(8L); existente.setTipoPrendaId(1); existente.setSucursalId(1);
        when(plazoParametroRepository.existsById(id)).thenReturn(true);
        when(plazoParametroRepository.findById(id)).thenReturn(Optional.of(existente));
        when(plazoParametroRepository.save(any(com.ignis.prestamil.model.PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.ignis.prestamil.request.PlazoParametroRequest req =
                new com.ignis.prestamil.request.PlazoParametroRequest();
        req.setPorcPrestamoSAvaluo(new BigDecimal("6"));
        req.setPorcSancionSemanal(new BigDecimal("2"));  // evita el default lookup

        // No debe lanzar excepción
        plazoService.guardarParametro(8L, 1, 1, req, "cajero1");

        verify(plazoParametroRepository).save(any());
    }
}
