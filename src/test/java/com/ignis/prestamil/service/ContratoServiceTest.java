package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.FolioBolsa;
import com.ignis.prestamil.model.FolioCodigoBarras;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.FolioBolsaRepository;
import com.ignis.prestamil.repository.FolioCodigoBarrasRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.TipoPrendaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.PartidaContratoRequest;
import com.ignis.prestamil.request.RecalcularPartidasRequest;
import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.PartidaRecalculadaResponse;
import com.ignis.prestamil.response.VencimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de regresión de la brecha de confianza servidor/cliente para partidas ALHAJA
 * (D-07, Pitfall 1 de PITFALLS.md) y de los rechazos de kilataje D-04/D-05.
 */
@ExtendWith(MockitoExtension.class)
class ContratoServiceTest {

    @Mock
    ContratoRepository repository;

    @Mock
    ClienteRepository clienteRepository;

    @Mock
    PlazoRepository plazoRepository;

    @Mock
    TurnoRepository turnoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    TipoPrendaRepository tipoPrendaRepository;

    @Mock
    CatValorPrendaRepository catValorPrendaRepository;

    @Mock
    ContratoMapper contratoMapper;

    @Mock
    PlazoParametroRepository plazoParametroRepository;

    @Mock
    PlazoService plazoService;

    @Mock
    PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;

    @Mock
    ParametrosSistemaCache parametrosSistemaCache;

    @Mock
    MovimientoContratoRepository movimientoContratoRepository;

    @Mock
    MovimientoCajaService movimientoCajaService;

    @Mock
    BolsaService bolsaService;

    CalculoContratoService calculoContratoService;
    ContratoService contratoService;

    @BeforeEach
    void setUp() {
        // Motor REAL (no mock): calcularAmortizacion ahora delega en el motor. Los tests de
        // amortizacion existentes esperan resultados numericos concretos que el motor debe replicar.
        calculoContratoService = new CalculoContratoService(parametrosSistemaCache);
        contratoService = new ContratoService(
                repository,
                clienteRepository,
                plazoRepository,
                turnoRepository,
                usuarioRepository,
                tipoPrendaRepository,
                catValorPrendaRepository,
                contratoMapper,
                plazoParametroRepository,
                plazoService,
                plazoHechuraAlhajaRepository,
                parametrosSistemaCache,
                calculoContratoService,
                movimientoContratoRepository,
                movimientoCajaService,
                bolsaService
        );

        // El snapshot del changeset 026 siempre pide el IVA vigente al crear un contrato.
        // Sin este stub los tests que persistan un contrato lanzarian NPE al leer el cache.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

        // Precio de avalúo por gramo de ALHAJA (valor del oro). Por defecto neutro = precio de
        // préstamo de la celda, así el avalúo real de los tests queda en precioPrestamo × peso. Los
        // tests del avalúo real como valor del oro lo sobreescriben con un valor mayor.
        lenient().when(plazoService.calcularPrecioAvaluoGramo(any(PlazoHechuraAlhaja.class)))
                .thenAnswer(inv -> inv.<PlazoHechuraAlhaja>getArgument(0).getPrecioPrestamo());

        // Stubs comunes a todos los tests: turno activo, cajero, cliente, plazo y tipo de prenda ALHAJA
        Turno turno = new Turno();
        turno.setId(1);
        lenient().when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turno));

        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        lenient().when(usuarioRepository.findByNombreUsuario("cajero1")).thenReturn(Optional.of(usuario));

        Cliente cliente = new Cliente();
        cliente.setId(1);
        lenient().when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(10);
        lenient().when(plazoRepository.findById(1L)).thenReturn(Optional.of(plazo));

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        lenient().when(tipoPrendaRepository.findById(1)).thenReturn(Optional.of(alhaja));

        lenient().when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.empty());

        TipoPrenda plata = new TipoPrenda();
        plata.setId(4);
        plata.setTipo("PLATAS");
        lenient().when(tipoPrendaRepository.findById(4)).thenReturn(Optional.of(plata));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ContratoRequest buildRequestBase() {
        ContratoRequest request = new ContratoRequest();
        request.setIdCliente(1);
        request.setIdPlazo(1L);
        request.setPartidas(new ArrayList<>());
        return request;
    }

    private PartidaContratoRequest buildPartidaAlhaja(BigDecimal avaluoRealSpoofed, Integer kilataje,
                                                       String hechura, BigDecimal pesoNeto,
                                                       BigDecimal montoPrestamo) {
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(1);
        pr.setDescripcion("Anillo de oro");
        pr.setKilataje(kilataje);
        pr.setHechura(hechura);
        pr.setPesoNeto(pesoNeto);
        pr.setAvaluoReal(avaluoRealSpoofed);
        pr.setMontoPrestamo(montoPrestamo);
        return pr;
    }

    /** Partida de plata: el avaluoReal enviado es deliberadamente "spoofed" (el servidor debe ignorarlo). */
    private PartidaContratoRequest buildPartidaPlata(BigDecimal avaluoRealSpoofed, BigDecimal ley,
                                                     BigDecimal pesoNeto, BigDecimal montoPrestamo) {
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(4);
        pr.setDescripcion("Pulsera de plata");
        pr.setLey(ley);
        pr.setPesoNeto(pesoNeto);
        pr.setPrecioXGramo(new BigDecimal("1234.00")); // spoofed: el servidor debe reemplazarlo
        pr.setAvaluoReal(avaluoRealSpoofed);
        pr.setMontoPrestamo(montoPrestamo);
        return pr;
    }

    /** Fixture de PlazoParametro con los valores reales al 2026-08-06 (D-01). */
    private PlazoParametro buildParametroPlata(BigDecimal ley925, BigDecimal ley725,
                                               BigDecimal porcPrestamoSAvaluo) {
        PlazoParametro p = new PlazoParametro();
        p.setLey925(ley925);
        p.setLey725(ley725);
        p.setPorcPrestamoSAvaluo(porcPrestamoSAvaluo);
        return p;
    }

    /**
     * Stubs minimos de persistencia. Usar en partidas sin PlazoParametro resuelto (p. ej. alhajas),
     * donde stubGuardadoExitoso dejaria el stub de calcularAvaluoContrato sin usar.
     */
    private void stubPersistencia() {
        when(contratoMapper.toResponse(any(Contrato.class))).thenAnswer(inv -> new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato c = inv.getArgument(0);
            if (c.getId() == null) { c.setId(1L); }
            return c;
        });
    }

    /** Stubs necesarios para que crearContrato llegue hasta repository.save sin NPE. */
    private void stubGuardadoExitoso() {
        stubPersistencia();
        // buildPartida llama a calcularAvaluoContrato cuando parametro != null; el mock
        // devolveria null y crearContrato hace totalAvaluo.add(null) -> NPE. Devolver el monto tal cual
        // reproduce el comportamiento real con usaAvaluoReal=false / porcPrestamoSAvaluoReal=0 (D-07).
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Extrae la unica partida del contrato capturado en repository.save. */
    private PartidaContrato capturarPartidaGuardada() {
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0).getPartidas().get(0);
    }

    private CatValorPrenda buildValorCatalogo(int valorId, int atributoId, TipoPrenda tipoPrenda) {
        CatSubtipoPrenda subtipo = new CatSubtipoPrenda();
        subtipo.setIdAtributo(atributoId);
        subtipo.setTipoPrenda(tipoPrenda);

        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(valorId);
        valor.setSubtipoPrenda(subtipo);
        valor.setDescripcion("Anillo 14K");
        return valor;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * C-07: la creación de un contrato entrega efectivo al cliente, así que registra una SALIDA de
     * caja por el préstamo total. Es la contraparte del ENTRADA que se registra al cancelar el EMP.
     */
    @Test
    void crearContrato_registraSalidaDeCajaPorElPrestamoEntregado() {
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        request.getPartidas().add(partida);

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        org.mockito.ArgumentCaptor<BigDecimal> monto = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        org.mockito.ArgumentCaptor<String> concepto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(movimientoCajaService).registrar(any(Turno.class),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(com.ignis.prestamil.model.TipoMovimientoCaja.SALIDA),
                concepto.capture(), monto.capture(),
                any(Usuario.class), any(MovimientoContrato.class));
        assertThat(monto.getValue()).isEqualByComparingTo("50.00");
        assertThat(concepto.getValue()).contains("PRÉSTAMO CONTRATO CTR-");
    }

    @Test
    void crearContrato_vinculaValorDeCatalogoDelMismoTipo() {
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        partida.setIdValorPrenda(25);
        request.getPartidas().add(partida);

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        TipoPrenda alhaja = tipoPrendaRepository.findById(1).orElseThrow();
        CatValorPrenda valorCatalogo = buildValorCatalogo(25, 4, alhaja);
        when(catValorPrendaRepository.findById(25)).thenReturn(Optional.of(valorCatalogo));
        when(contratoMapper.toResponse(any(Contrato.class))).thenReturn(new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato contrato = inv.getArgument(0);
            if (contrato.getId() == null) contrato.setId(1L);
            return contrato;
        });

        contratoService.crearContrato(request, "cajero1");

        assertThat(capturarPartidaGuardada().getValorPrenda()).isSameAs(valorCatalogo);
    }

    @Test
    void crearContrato_copiaClaveYDescripcionGeneralDelCatalogo_noLasDelCliente() {
        // D-09: clave y descripción general son el estándar del catálogo; el cliente no puede
        // mandar otras. Se copian a la partida para que el contrato firmado no cambie si después
        // Sistemas edita el catálogo.
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        partida.setIdValorPrenda(25);
        partida.setClavePrenda("OTRA");
        partida.setDescripcion("Esclava con mariposa, broche roto");
        request.getPartidas().add(partida);

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));
        CatValorPrenda valorCatalogo = buildValorCatalogo(25, 4, tipoPrendaRepository.findById(1).orElseThrow());
        valorCatalogo.setClave("ESC14");
        valorCatalogo.setDescripcion("ESCLAVA 14K");
        when(catValorPrendaRepository.findById(25)).thenReturn(Optional.of(valorCatalogo));
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getClavePrenda()).isEqualTo("ESC14");
        assertThat(guardada.getDescripcionGeneral()).isEqualTo("ESCLAVA 14K");
        assertThat(guardada.getDescripcion()).isEqualTo("Esclava con mariposa, broche roto");

        // Editar el catálogo después no toca el snapshot de la partida
        valorCatalogo.setDescripcion("ESCLAVA ORO 14K");
        assertThat(guardada.getDescripcionGeneral()).isEqualTo("ESCLAVA 14K");
    }

    @Test
    void crearContrato_descripcionEspecificaCorta_rechazaConNumeroDePartida() {
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        partida.setDescripcion("Celular");
        request.getPartidas().add(partida);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));

        assertThat(ex.getMessage()).contains("Partida 1").contains("al menos 10 caracteres");
    }

    @Test
    void crearContrato_variosSinMarcaNiModelo_rechazaListandoLosFaltantes() {
        TipoPrenda varios = new TipoPrenda();
        varios.setId(3);
        varios.setTipo("VARIOS");
        when(tipoPrendaRepository.findById(3)).thenReturn(Optional.of(varios));

        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(3);
        pr.setDescripcion("Bocina bluetooth negra con cable");
        pr.setSerieImei("SN-1");
        pr.setAvaluoReal(new BigDecimal("1000.00"));
        pr.setMontoPrestamo(new BigDecimal("500.00"));
        request.getPartidas().add(pr);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));

        assertThat(ex.getMessage()).contains("VARIOS").contains("marca, modelo");
    }

    @Test
    void crearContrato_partidaAlhaja_persisteDescripcionLibreDeLaPartida() {
        // Given: descripcion libre explicitamente distinta al nombre del catalogo, para probar
        // que ContratoService.buildPartida ya conecta pr.getDescripcion() -> partida.setDescripcion()
        // sin depender de ningun valor de catalogo (RESEARCH.md riesgo 1, ya resuelto en codigo).
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        partida.setDescripcion("Anillo tallado a mano, iniciales J.M.");
        request.getPartidas().add(partida);

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));
        // No se usa stubGuardadoExitoso() aqui: esa stubea plazoService.calcularAvaluoContrato,
        // que solo se invoca cuando parametro != null (partidas Plata). Esta partida es Alhaja
        // (parametro null), y ese stub quedaria sin uso -> Mockito UnnecessaryStubbingException.
        when(contratoMapper.toResponse(any(Contrato.class))).thenReturn(new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato contrato = inv.getArgument(0);
            if (contrato.getId() == null) contrato.setId(1L);
            return contrato;
        });

        contratoService.crearContrato(request, "cajero1");

        assertThat(capturarPartidaGuardada().getDescripcion())
                .isEqualTo("Anillo tallado a mano, iniciales J.M.");
    }

    @Test
    void crearContrato_rechazaValorDeCatalogoDeOtroTipo() {
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00"));
        partida.setIdValorPrenda(25);
        request.getPartidas().add(partida);

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        TipoPrenda varios = new TipoPrenda();
        varios.setId(3);
        varios.setTipo("VARIOS");
        when(catValorPrendaRepository.findById(25))
                .thenReturn(Optional.of(buildValorCatalogo(25, 6, varios)));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));

        assertThat(error.getMessage()).contains("no pertenece al tipo de prenda");
    }

    @Test
    void crearContrato_partidaAlhaja_ignoraAvaluoRealDelCliente() {
        // Given: avaluoReal spoofed muy por encima del real calculado por el servidor
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("999999.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("500.00")));

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setId(new PlazoHechuraAlhajaId(1, 1, 14, "N"));
        tabla.setPrecioPrestamo(new BigDecimal("1200.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        when(contratoMapper.toResponse(any(Contrato.class)))
                .thenAnswer(inv -> new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: el avaluoReal persistido es el calculado por el servidor, NO el spoofed
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato captured = captor.getAllValues().get(0);
        BigDecimal avaluoRealPersistido = captured.getPartidas().get(0).getAvaluoReal();

        assertThat(avaluoRealPersistido.compareTo(new BigDecimal("999999.00"))).isNotEqualTo(0);
        assertThat(avaluoRealPersistido.compareTo(new BigDecimal("12000.00"))).isEqualTo(0);
    }

    @Test
    void crearContrato_partidaAlhaja_kilataje24K_rechazaConMensajeClaro() {
        // Given
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1.00"), 24, "N",
                new BigDecimal("5.0000"), new BigDecimal("100.00")));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).isEqualTo("Oro de 24K no es prendable");
    }

    @Test
    void crearContrato_partidaAlhaja_kilatajeNoSoportado_rechaza() {
        // Given: 16K no está en la tabla COCAE de kilatajes soportados
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1.00"), 16, "N",
                new BigDecimal("5.0000"), new BigDecimal("100.00")));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).isEqualTo("Kilataje no soportado: 16");
    }

    @Test
    void crearContrato_partidaAlhaja_montoPrestamoSuperaMaximoCalculadoPorServidor_rechaza() {
        // Given: misma celda del test 1 (avaluo real servidor = 12000.00), pero montoPrestamo
        // "coincide" con el avaluoReal spoofed del cliente (999999.00), que excede el techo real
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("999999.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("999999.00")));

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setId(new PlazoHechuraAlhajaId(1, 1, 14, "N"));
        tabla.setPrecioPrestamo(new BigDecimal("1200.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("supera el máximo autorizado");
    }

    // -----------------------------------------------------------------------
    // Tests PLATA (Phase 6 — PLATA-01/02/03)
    // -----------------------------------------------------------------------

    @Test
    void crearContrato_partidaPlata_ley925_calculaAvaluoServidor() {
        // Given: peso 10 g x ley925 6.5 -> avaluo 65.00 (D-01), prestamo 32.50 = 65.00 x 50%
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("999999.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("32.50")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: avaluoReal y precioXGramo son los del servidor, no los del cliente
        PartidaContrato partida = capturarPartidaGuardada();
        assertThat(partida.getAvaluoReal().compareTo(new BigDecimal("65.00"))).isEqualTo(0);
        assertThat(partida.getPrecioXGramo().compareTo(new BigDecimal("6.5"))).isEqualTo(0);
    }

    @Test
    void crearContrato_partidaPlata_ley720_calculaAvaluoServidor() {
        // Given: peso 10 g x precio de la ley baja 5.0 -> avaluo 50.00 (D-01). Ley 720 (estándar COCAE),
        // con ceros de cola (round-trip BD). El precio sigue almacenado en la columna ley_725.
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("1.00"), new BigDecimal("720.0000"),
                new BigDecimal("10.0000"), new BigDecimal("25.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then
        PartidaContrato partida = capturarPartidaGuardada();
        assertThat(partida.getAvaluoReal().compareTo(new BigDecimal("50.00"))).isEqualTo(0);
        assertThat(partida.getPrecioXGramo().compareTo(new BigDecimal("5.0"))).isEqualTo(0);
    }

    @Test
    void crearContrato_partidaPlata_ignoraAvaluoRealDelCliente() {
        // Given: avaluoReal spoofed 999999.00, pero el prestamo pedido respeta el techo real (32.50)
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("999999.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("30.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then
        PartidaContrato partida = capturarPartidaGuardada();
        assertThat(partida.getAvaluoReal().compareTo(new BigDecimal("999999.00"))).isNotEqualTo(0);
        assertThat(partida.getAvaluoReal().compareTo(new BigDecimal("65.00"))).isEqualTo(0);
    }

    @Test
    void crearContrato_partidaPlata_precioGramoCero_rechaza() {
        // Given: fila plazo_parametro real recien migrada -> ley_925 = 0.0000 (Verified Finding 1)
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("100.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("50.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

        // When / Then: falla ruidosamente, nunca avaluo 0 silencioso
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("Precio por gramo no configurado para ley");
    }

    @Test
    void crearContrato_partidaPlata_leyNoSoportada_rechaza() {
        // Given: ley 800 no es 925 ni 720
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("100.00"), new BigDecimal("800"),
                new BigDecimal("10.0000"), new BigDecimal("50.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("Ley no soportada: 800");
    }

    @Test
    void calcularAmortizacion_desglosaInteresAlmacenIvaDesempeno_comoCOCAE() {
        // Given: contrato de plata (ejemplo verificado en flujo-plata) — préstamo 630, plazo
        // semanal 7 x 4, interés 2.9%, almacén 0.6%, total 3.5%. IVA 16% TRUNCADO (regla COCAE).
        TipoPrenda plata = new TipoPrenda();
        plata.setId(4);
        plata.setTipo("PLATAS");
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(plata);

        Plazo plazoSemanal = new Plazo();
        plazoSemanal.setId(6L);
        plazoSemanal.setDiasPorPeriodo(7);
        plazoSemanal.setNumeroPeriodos(4);

        Contrato contrato = new Contrato();
        contrato.setId(1L);
        contrato.setMontoPrestamo(new BigDecimal("630.00"));
        contrato.setFechaApertura(LocalDateTime.of(2026, 8, 15, 0, 0));
        contrato.setSucursalId(1);
        contrato.setPlazo(plazoSemanal);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        contrato.setPartidas(partidas);
        when(repository.findById(1L)).thenReturn(Optional.of(contrato));

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("2.9"));
        parametro.setPorcAlmacen(new BigDecimal("0.6"));
        parametro.setPorcGastosAdmin(new BigDecimal("0"));
        parametro.setPorcInteresTotal(new BigDecimal("3.5"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(parametro));

        // When
        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(1L);

        // Then: 4 filas con los montos exactos de la captura de COCAE (image6 de Flujo de Plata)
        assertThat(filas).hasSize(4);
        VencimientoResponse v1 = filas.get(0);
        assertThat(v1.getInteres()).isEqualByComparingTo("18.27");
        assertThat(v1.getAlmacen()).isEqualByComparingTo("3.78");
        assertThat(v1.getTotalInteres()).isEqualByComparingTo("22.05");
        assertThat(v1.getIva()).isEqualByComparingTo("3.52");   // 22.05 x 16% = 3.528 -> TRUNCADO 3.52
        assertThat(v1.getDesempeno()).isEqualByComparingTo("655.57");

        VencimientoResponse v4 = filas.get(3);
        assertThat(v4.getTotalInteres()).isEqualByComparingTo("88.20");
        assertThat(v4.getIva()).isEqualByComparingTo("14.11");
        assertThat(v4.getDesempeno()).isEqualByComparingTo("732.31");
    }

    @Test
    void crearContrato_partidaPlata_montoPrestamoSuperaMaximoCalculadoPorServidor_rechaza() {
        // Given: para PLATA el techo es peso x precio (COCAE: el precio por gramo YA es el prestamo),
        // NO se aplica % Prestamo s/Avaluo aunque este configurado (50). peso 11.8615 x 6.5 = 77.10.
        // El cliente pide 100.00 -> rechazado por exceder 77.10.
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("999999.00"), new BigDecimal("925"),
                new BigDecimal("11.8615"), new BigDecimal("100.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("supera el máximo autorizado");
        assertThat(ex.getMessage()).contains("77.10");
    }

    @Test
    void crearContrato_partidaPlata_ignoraPorcentajePrestamoSAvaluo_techoEsPesoPorPrecio() {
        // Given: peso 20 x ley925 6.5 = 130. Aunque el plazo tenga % Prestamo s/Avaluo = 50,
        // para plata NO se recorta: el techo sigue siendo 130 (con el bug viejo habria sido 65).
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("999999.00"), new BigDecimal("925"),
                new BigDecimal("20.0000"), new BigDecimal("130.00")));   // pide justo el maximo COCAE
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: 130 aceptado (no recortado a 65 por el 50%)
        PartidaContrato partida = capturarPartidaGuardada();
        assertThat(partida.getMontoPrestamo()).isEqualByComparingTo("130.00");
        assertThat(partida.getAvaluoReal()).isEqualByComparingTo("130.00");
    }

    // -----------------------------------------------------------------------
    // Tests PESO NETO vs PESO TOTAL (confirmado con Jorge 2026-09-08)
    //
    // Una pieza de oro puede traer piedras o soldadura: el peso neto es solo el metal
    // (y es el que se cobra), el peso total es lo que pesa fisicamente la pieza completa.
    // -----------------------------------------------------------------------

    /** Stub de la tabla de precios de oro para 14K hechura "N". */
    private void stubTablaAlhaja(String precioPrestamo) {
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal(precioPrestamo));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));
    }

    @Test
    void crearContrato_partidaAlhaja_persistePesoNetoYPesoTotalPorSeparado() {
        // Given: aretes de 2.5 g de los cuales solo 2 g son oro (ejemplo literal de Jorge)
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("200.00"), 14, "N",
                new BigDecimal("2.0000"), new BigDecimal("200.00"));
        partida.setPesoTotal(new BigDecimal("2.5000"));
        request.getPartidas().add(partida);
        stubTablaAlhaja("100.0000");
        stubPersistencia();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: los dos pesos se guardan, y el avaluo sale del NETO (2 x 100 = 200, no 250)
        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getPesoNeto()).isEqualByComparingTo("2.0000");
        assertThat(guardada.getPesoTotal()).isEqualByComparingTo("2.5000");
        assertThat(guardada.getAvaluoReal()).isEqualByComparingTo("200.00");
    }

    @Test
    void crearContrato_partidaAlhaja_sinPesoTotal_loIgualaAlNeto() {
        // Given: el usuario deja el peso total vacio -> la pieza se asume 100% metal
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("300.00"), 14, "N",
                new BigDecimal("3.0000"), new BigDecimal("300.00"));
        partida.setPesoTotal(null);
        request.getPartidas().add(partida);
        stubTablaAlhaja("100.0000");
        stubPersistencia();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then
        assertThat(capturarPartidaGuardada().getPesoTotal()).isEqualByComparingTo("3.0000");
    }

    @Test
    void crearContrato_partidaAlhaja_pesoTotalMenorAlNeto_lanzaBadRequest() {
        // Given: peso total 1.5 g con peso neto 2 g -> fisicamente imposible
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("200.00"), 14, "N",
                new BigDecimal("2.0000"), new BigDecimal("200.00"));
        partida.setPesoTotal(new BigDecimal("1.5000"));
        request.getPartidas().add(partida);
        stubTablaAlhaja("100.0000");

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("no puede ser menor que el peso neto");
    }

    @Test
    void crearContrato_partidaAlhaja_cantidadNoMultiplicaElPeso() {
        // Given: 2 aretes con peso neto 2 g DEL LOTE (no 2 g cada uno). El prestamo debe salir
        // sobre 2 g (200.00) y nunca sobre 4 g (400.00): cantidad es solo informativa.
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaAlhaja(
                new BigDecimal("200.00"), 14, "N",
                new BigDecimal("2.0000"), new BigDecimal("200.00"));
        partida.setCantidad(2);
        request.getPartidas().add(partida);
        stubTablaAlhaja("100.0000");
        stubPersistencia();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then
        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getCantidad()).isEqualTo(2);
        assertThat(guardada.getAvaluoReal()).isEqualByComparingTo("200.00");
        assertThat(guardada.getMontoPrestamo()).isEqualByComparingTo("200.00");
    }

    @Test
    void crearContrato_partidaPlata_persistePesoNetoYPesoTotalPorSeparado() {
        // Given: pulsera de plata con dije de resina: 10 g de plata dentro de 12 g totales
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaPlata(
                new BigDecimal("999999.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("65.00"));
        partida.setPesoTotal(new BigDecimal("12.0000"));
        request.getPartidas().add(partida);
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), new BigDecimal("50"))));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: avaluo = 10 x 6.5 = 65.00, calculado sobre el neto y no sobre los 12 g
        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getPesoNeto()).isEqualByComparingTo("10.0000");
        assertThat(guardada.getPesoTotal()).isEqualByComparingTo("12.0000");
        assertThat(guardada.getAvaluoReal()).isEqualByComparingTo("65.00");
    }

    // =========================================================================
    // Snapshot de calculo (changeset 026, Pasada 1) — se persiste al crear el contrato
    // aunque en Pasada 1 el motor todavia no lo consume. Verificar aqui evita que la
    // Pasada 2 empiece con contratos sin snapshot y regresiones por fallback a config.
    // =========================================================================

    @Test
    void crearContrato_snapshot_persiste_los_7_campos_del_plazoParametro_e_iva_vigente() {
        // Given: partida de plata con un PlazoParametro completo (los 7 campos)
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest partida = buildPartidaPlata(
                new BigDecimal("100.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("50.00"));
        request.getPartidas().add(partida);

        PlazoParametro param = new PlazoParametro();
        param.setLey925(new BigDecimal("6.5"));
        param.setLey725(new BigDecimal("5.0"));
        param.setPorcPrestamoSAvaluo(new BigDecimal("50"));
        // Los 6 campos que van al snapshot (ademas del IVA que viene del cache mockeado a 16.00)
        param.setPorcInteres(new BigDecimal("3.0000"));
        param.setPorcAlmacen(new BigDecimal("2.0000"));
        param.setPorcGastosAdmin(new BigDecimal("1.0000"));
        param.setPorcSancionSemanal(new BigDecimal("2.5000"));
        param.setDiasGraciaSinInteres(3);
        param.setAplicarSancionPorPeriodo(true);
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(param));
        stubGuardadoExitoso();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: los 7 campos snap_* del contrato quedan poblados con la config vigente
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato guardado = captor.getAllValues().get(0);

        assertThat(guardado.getSnapPorcSancionSemanal()).isEqualByComparingTo("2.5000");
        assertThat(guardado.getSnapDiasGraciaSancion()).isEqualTo(3);
        assertThat(guardado.getSnapAplicarSancionPeriodo()).isTrue();
        assertThat(guardado.getSnapIvaPorcentaje()).isEqualByComparingTo("16.00");
        assertThat(guardado.getSnapPorcInteres()).isEqualByComparingTo("3.0000");
        assertThat(guardado.getSnapPorcAlmacen()).isEqualByComparingTo("2.0000");
        assertThat(guardado.getSnapPorcGastosAdmin()).isEqualByComparingTo("1.0000");
    }

    @Test
    void crearContrato_snapshot_sin_plazoParametro_guarda_solo_iva_y_deja_6_campos_null() {
        // Given: partida ALHAJA con precio configurado pero SIN PlazoParametro
        // (setUp devuelve Optional.empty para plazoParametroRepository.find de idPlazo=1/tipo=1).
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1000.00"), 14, "N",
                new BigDecimal("5.0000"), new BigDecimal("500.00")));
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));
        stubPersistencia();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: sin PlazoParametro los 6 campos de plazo quedan null (el motor de Pasada 2
        // hara fallback a config vigente), pero el snapshot del IVA sí se guarda del cache.
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato guardado = captor.getAllValues().get(0);

        assertThat(guardado.getSnapPorcSancionSemanal()).isNull();
        assertThat(guardado.getSnapDiasGraciaSancion()).isNull();
        assertThat(guardado.getSnapAplicarSancionPeriodo()).isNull();
        assertThat(guardado.getSnapPorcInteres()).isNull();
        assertThat(guardado.getSnapPorcAlmacen()).isNull();
        assertThat(guardado.getSnapPorcGastosAdmin()).isNull();
        assertThat(guardado.getSnapIvaPorcentaje()).isEqualByComparingTo("16.00");
    }

    // =========================================================================
    // Changeset 027 (F0): saldo, fecha de contrato, comercializacion y movimiento EMP
    // =========================================================================

    @Test
    void crearContrato_inicializaSaldoYFechas_eInsertaMovimientoEmp() {
        // Given: alhaja de 5 g a $100/g, prestamo 500; plazo del setUp = 10 periodos de 7 dias
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1000.00"), 14, "N",
                new BigDecimal("5.0000"), new BigDecimal("500.00")));
        stubTablaAlhaja("100.0000");
        stubPersistencia();

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: saldo = prestamo; fecha_contrato = dia de apertura; comercializacion = venc + 15
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato guardado = captor.getAllValues().get(0);
        assertThat(guardado.getSaldoCapital()).isEqualByComparingTo("500.00");
        assertThat(guardado.getFechaContrato()).isEqualTo(guardado.getFechaApertura().toLocalDate());
        assertThat(guardado.getFechaVencimiento()).isEqualTo(guardado.getFechaContrato().plusDays(70));
        assertThat(guardado.getFechaComercializacion()).isEqualTo(guardado.getFechaVencimiento().plusDays(15));

        // Y se registra exactamente un EMP con el estado inicial del contrato
        ArgumentCaptor<MovimientoContrato> movCaptor = ArgumentCaptor.forClass(MovimientoContrato.class);
        org.mockito.Mockito.verify(movimientoContratoRepository).save(movCaptor.capture());
        MovimientoContrato emp = movCaptor.getValue();
        assertThat(emp.getTipo()).isEqualTo(TipoMovimiento.EMP);
        assertThat(emp.getContrato()).isSameAs(guardado);
        assertThat(emp.getMonto()).isEqualByComparingTo("500.00");
        // D-11: snapshot "pre-contrato" para que la cancelación del día (C-06) reverta a saldo 0
        // y estatus CANCELADO sin lógica especial en el revert genérico.
        assertThat(emp.getSaldoAnterior()).isEqualByComparingTo("0");
        assertThat(emp.getEstatusAnterior()).isEqualTo(EstatusContrato.CANCELADO);
        assertThat(emp.getNumRefrendosAnterior()).isZero();
        assertThat(emp.getSaldoNuevo()).isEqualByComparingTo("500.00");
        assertThat(emp.getFechaContratoNueva()).isEqualTo(guardado.getFechaContrato());
        assertThat(emp.getFechaVencNueva()).isEqualTo(guardado.getFechaVencimiento());
        assertThat(emp.getEstatusNuevo()).isEqualTo(EstatusContrato.VIGENTE);
        assertThat(emp.getFecha()).isEqualTo(guardado.getFechaApertura());
    }

    // =========================================================================
    // D-10 — bolsa y código de barras por partida
    // =========================================================================

    /**
     * Dos contratos seguidos en la misma sucursal: bolsas consecutivas y códigos de barras distintos y
     * consecutivos, uno por partida. Usa el BolsaService real sobre contadores en memoria (el bloqueo
     * real lo prueba BolsaServiceIT).
     */
    @Test
    void crearContrato_dosContratosSeguidos_obtienenBolsasConsecutivasYCodigosDeBarrasUnicos() {
        FolioBolsa folioBolsa = new FolioBolsa();
        folioBolsa.setSucursalId(1);
        folioBolsa.setUltimoFolio(3968);
        FolioBolsaRepository folioBolsaRepository = org.mockito.Mockito.mock(FolioBolsaRepository.class);
        when(folioBolsaRepository.findBySucursalId(1)).thenReturn(Optional.of(folioBolsa));

        FolioCodigoBarras folioCodigo = new FolioCodigoBarras();
        folioCodigo.setId(FolioCodigoBarras.ID_UNICO);
        folioCodigo.setUltimoFolio(40L);
        FolioCodigoBarrasRepository folioCodigoRepository =
                org.mockito.Mockito.mock(FolioCodigoBarrasRepository.class);
        when(folioCodigoRepository.findWithLockById(FolioCodigoBarras.ID_UNICO)).thenReturn(Optional.of(folioCodigo));

        ContratoService conBolsaReal = new ContratoService(repository, clienteRepository, plazoRepository,
                turnoRepository, usuarioRepository, tipoPrendaRepository, catValorPrendaRepository, contratoMapper,
                plazoParametroRepository, plazoService, plazoHechuraAlhajaRepository, parametrosSistemaCache,
                calculoContratoService, movimientoContratoRepository, movimientoCajaService,
                new BolsaService(folioBolsaRepository, folioCodigoRepository));
        stubTablaAlhaja("100.0000");
        stubPersistencia();

        // Primer contrato con dos partidas, segundo con una
        ContratoRequest primero = buildRequestBase();
        primero.getPartidas().add(buildPartidaAlhaja(new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00")));
        primero.getPartidas().add(buildPartidaAlhaja(new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00")));
        ContratoRequest segundo = buildRequestBase();
        segundo.getPartidas().add(buildPartidaAlhaja(new BigDecimal("100.00"), 14, "N",
                BigDecimal.ONE, new BigDecimal("50.00")));

        conBolsaReal.crearContrato(primero, "cajero1");
        conBolsaReal.crearContrato(segundo, "cajero1");

        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<Contrato> contratos = captor.getAllValues().stream().distinct().toList();
        assertThat(contratos).hasSize(2);
        assertThat(contratos.get(0).getNumBolsa()).isEqualTo(3969);
        assertThat(contratos.get(1).getNumBolsa()).isEqualTo(3970);

        List<String> codigos = contratos.stream()
                .flatMap(c -> c.getPartidas().stream())
                .map(PartidaContrato::getCodigoBarras)
                .toList();
        assertThat(codigos).containsExactly("0000000041", "0000000042", "0000000043");
        assertThat(folioCodigo.getUltimoFolio()).isEqualTo(43L);
    }

    /** Si el contrato se rechaza antes de guardarse, no se consume bolsa ni código de barras. */
    @Test
    void crearContrato_rechazado_noTomaBolsaNiCodigoDeBarras() {
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(new BigDecimal("100.00"), 24, "N",
                BigDecimal.ONE, new BigDecimal("50.00")));

        assertThrows(BadRequestException.class, () -> contratoService.crearContrato(request, "cajero1"));

        org.mockito.Mockito.verifyNoInteractions(bolsaService);
    }

    // =========================================================================
    // C-08 / RN-30 — redondeo del total de alhajas al múltiplo de $5 más cercano
    // =========================================================================

    @Test
    void crearContrato_alhaja_totalRedondeadoAMultiploDe5_acepta() {
        // Given: una sola partida con préstamo exacto a múltiplo de $5 (7,800)
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("8000.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("7800.00")));
        stubTablaAlhaja("800.0000");
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        assertThat(capturarPartidaGuardada().getMontoPrestamo()).isEqualByComparingTo("7800.00");
    }

    @Test
    void crearContrato_alhaja_unaPartidaConCentavos_rechaza() {
        // 7,802.30 no es múltiplo de $5 (floor $5 = 7,800). El frontend debió ajustarlo
        // antes de enviar; aquí se rechaza como salvaguarda (C-08).
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("8000.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("7802.30")));
        stubTablaAlhaja("800.0000");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("múltiplo de $5");
        assertThat(ex.getMessage()).contains("7800.00");
    }

    @Test
    void crearContrato_alhajaMultiPartida_ultimaDescontada_acepta() {
        // Escenario COCAE del plan: 7,847 (collar) + 698 (anillo ajustado) = 8,545, múltiplo de $5.
        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest collar = buildPartidaAlhaja(
                new BigDecimal("8000.00"), 14, "N",
                new BigDecimal("11.2100"), new BigDecimal("7847.00"));
        PartidaContratoRequest anillo = buildPartidaAlhaja(
                new BigDecimal("800.00"), 14, "N",
                new BigDecimal("1.0100"), new BigDecimal("698.00"));
        request.getPartidas().add(collar);
        request.getPartidas().add(anillo);
        stubTablaAlhaja("700.0000");
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato guardado = captor.getAllValues().get(0);
        assertThat(guardado.getMontoPrestamo()).isEqualByComparingTo("8545.00");
    }

    @Test
    void crearContrato_alhajaMultiPartida_sinDescuentoEnUltima_rechaza() {
        // Mismas partidas pero sin aplicar el ajuste (7,847 + 701 = 8,548, no múltiplo de $5).
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("8000.00"), 14, "N",
                new BigDecimal("11.2100"), new BigDecimal("7847.00")));
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("800.00"), 14, "N",
                new BigDecimal("1.0100"), new BigDecimal("701.00")));
        stubTablaAlhaja("700.0000");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("múltiplo de $5");
        assertThat(ex.getMessage()).contains("8545.00");
    }

    @Test
    void crearContrato_alhaja_prestamoYaMultiploDe5_sinAjuste_acepta() {
        // 7,850 ya es múltiplo de $5: no requiere ajuste, se acepta tal cual.
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("8000.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("7850.00")));
        stubTablaAlhaja("785.0000");
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        assertThat(capturarPartidaGuardada().getMontoPrestamo()).isEqualByComparingTo("7850.00");
    }

    @Test
    void crearContrato_varios_prestamoConCentavos_noAplicaRedondeo_acepta() {
        // Electrónicos / Varios: la regla C-08 NO aplica (TODO G-04). Un préstamo de $503.75
        // se acepta aunque no sea múltiplo de $5.
        TipoPrenda varios = new TipoPrenda();
        varios.setId(3);
        varios.setTipo("VARIOS");
        when(tipoPrendaRepository.findById(3)).thenReturn(Optional.of(varios));

        ContratoRequest request = buildRequestBase();
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(3);
        pr.setDescripcion("Laptop gris, cargador original");
        pr.setMarca("Dell");
        pr.setModelo("Latitude 5420");
        pr.setSerieImei("SN-48291");
        pr.setAvaluoReal(new BigDecimal("1000.00"));
        pr.setMontoPrestamo(new BigDecimal("503.75"));
        request.getPartidas().add(pr);
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        assertThat(capturarPartidaGuardada().getMontoPrestamo()).isEqualByComparingTo("503.75");
    }

    @Test
    void calcularAmortizacion_usaElPrestamoRedondeadoDelContrato() {
        // La tabla de amortización se calcula sobre contrato.montoPrestamo. Al persistirse éste
        // ya redondeado (C-08), toda la tabla usa el total ajustado; aquí lo verificamos.
        TipoPrenda alhaja = tipoPrendaRepository.findById(1).orElseThrow();
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(alhaja);

        Plazo plazoSemanal = new Plazo();
        plazoSemanal.setId(6L);
        plazoSemanal.setDiasPorPeriodo(7);
        plazoSemanal.setNumeroPeriodos(4);

        Contrato contrato = new Contrato();
        contrato.setId(2L);
        contrato.setMontoPrestamo(new BigDecimal("8545.00"));   // redondeado a $5
        contrato.setFechaApertura(LocalDateTime.of(2026, 10, 1, 0, 0));
        contrato.setSucursalId(1);
        contrato.setPlazo(plazoSemanal);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        contrato.setPartidas(partidas);
        when(repository.findById(2L)).thenReturn(Optional.of(contrato));

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("1.0"));
        parametro.setPorcAlmacen(new BigDecimal("0"));
        parametro.setPorcGastosAdmin(new BigDecimal("0"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 1, 1))
                .thenReturn(Optional.of(parametro));

        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(2L);

        // Interés del primer periodo = 8545 × 1% = 85.45, derivado del total YA redondeado.
        assertThat(filas).hasSize(4);
        assertThat(filas.get(0).getInteres()).isEqualByComparingTo("85.45");
    }

    @Test
    void crearContrato_rechazado_noInsertaMovimientoEmp() {
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1000.00"), 24, "N",
                new BigDecimal("5.0000"), new BigDecimal("500.00")));

        assertThrows(BadRequestException.class, () -> contratoService.crearContrato(request, "cajero1"));

        org.mockito.Mockito.verify(movimientoContratoRepository, org.mockito.Mockito.never())
                .save(any(MovimientoContrato.class));
    }

    // =========================================================================
    // D-05: tabla de amortización — columna de almacenaje bien separada del
    // interés, filas monótonas. Casos de referencia C19 (préstamo 575.08) y
    // C20 (contrato 1493 de COCAE). Tolerancia ±0.03 (plan 07-oct 2.7).
    // =========================================================================

    /** Monta un contrato semanal de 4 periodos para calcularAmortizacion. */
    private Contrato fixtureContratoSemanal(BigDecimal prestamo) {
        TipoPrenda plata = new TipoPrenda();
        plata.setId(4); plata.setTipo("PLATAS");
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(plata);

        Plazo plazo = new Plazo();
        plazo.setId(6L); plazo.setDiasPorPeriodo(7); plazo.setNumeroPeriodos(4);

        Contrato c = new Contrato();
        c.setId(99L);
        c.setMontoPrestamo(prestamo);
        c.setFechaApertura(LocalDateTime.of(2026, 8, 15, 0, 0));
        c.setSucursalId(1);
        c.setPlazo(plazo);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        c.setPartidas(partidas);
        return c;
    }

    private void assertCercano(BigDecimal actual, String esperado, String etiqueta) {
        BigDecimal esp = new BigDecimal(esperado);
        BigDecimal tol = new BigDecimal("0.03");
        assertThat(actual.subtract(esp).abs())
                .as("%s: esperado %s, obtenido %s (tolerancia ±0.03)", etiqueta, esperado, actual)
                .isLessThanOrEqualTo(tol);
    }

    @Test
    void calcularAmortizacion_C19_filasNormalesS1aS4() {
        // C19 — préstamo 575.08, i 1.55%, a 0.6%, s 2%, 4 semanas + 2 extemporáneas (plata).
        // Fila por fila contra los valores esperados del plan 07-oct 2.7, con tolerancia ±0.03
        // porque el motor redondea cada componente a 2 dec antes de sumar (diferencias ≤ $0.01).
        Contrato contrato = fixtureContratoSemanal(new BigDecimal("575.08"));
        when(repository.findById(99L)).thenReturn(Optional.of(contrato));

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("1.55"));
        parametro.setPorcAlmacen(new BigDecimal("0.6"));
        parametro.setPorcGastosAdmin(new BigDecimal("0"));
        parametro.setPorcSancionSemanal(new BigDecimal("2"));
        parametro.setAplicarSancionPorPeriodo(true);
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(parametro));

        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(99L);

        assertThat(filas).hasSize(4);

        // S1: 8.91 / 3.45 / 1.98 / 14.34 (interes / almacen / iva / refrendo)
        VencimientoResponse s1 = filas.get(0);
        assertCercano(s1.getInteres(),      "8.91",  "S1 interes");
        assertCercano(s1.getAlmacen(),      "3.45",  "S1 almacen");
        assertCercano(s1.getIva(),          "1.98",  "S1 iva");
        assertCercano(s1.getTotalInteres().add(s1.getIva()),
                                            "14.34", "S1 refrendo");
        // Clave del bug D-05: almacen NUNCA debe igualar al interes en una fila normal.
        assertThat(s1.getAlmacen()).isNotEqualByComparingTo(s1.getInteres());

        // S4: 35.65 / 13.80 / 7.91 / 57.37
        VencimientoResponse s4 = filas.get(3);
        assertCercano(s4.getInteres(),      "35.65", "S4 interes");
        assertCercano(s4.getAlmacen(),      "13.80", "S4 almacen");
        assertCercano(s4.getIva(),          "7.91",  "S4 iva");
        assertCercano(s4.getTotalInteres().add(s4.getIva()),
                                            "57.37", "S4 refrendo");
    }

    @Test
    void calcularAmortizacion_C20_contra_COCAE_contrato1493() {
        // C20 — contrato 1493 de COCAE (S 1,195, i 1.13%, a 0.6%, s 2%). Filas S1–S4 normales
        // contra los valores de 2.6 (tabla de amortización) con tolerancia ±0.03.
        Contrato contrato = fixtureContratoSemanal(new BigDecimal("1195.00"));
        when(repository.findById(99L)).thenReturn(Optional.of(contrato));

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("1.13"));
        parametro.setPorcAlmacen(new BigDecimal("0.6"));
        parametro.setPorcGastosAdmin(new BigDecimal("0"));
        parametro.setPorcSancionSemanal(new BigDecimal("2"));
        parametro.setAplicarSancionPorPeriodo(true);
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(parametro));

        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(99L);

        assertThat(filas).hasSize(4);

        // S1 esperado: interes 13.50, almacen 7.17, iva 3.30, refrendo 23.97, desempeno 1,218.97
        VencimientoResponse s1 = filas.get(0);
        assertCercano(s1.getInteres(),   "13.50",    "1493 S1 interes");
        assertCercano(s1.getAlmacen(),   "7.17",     "1493 S1 almacen");
        assertCercano(s1.getIva(),       "3.30",     "1493 S1 iva");
        assertCercano(s1.getTotalInteres().add(s1.getIva()),
                                         "23.97",    "1493 S1 refrendo");
        assertCercano(s1.getDesempeno(), "1218.97",  "1493 S1 desempeno");
        // Regresión D-05: almacen ≠ interes.
        assertThat(s1.getAlmacen()).isNotEqualByComparingTo(s1.getInteres());

        // S4 esperado: interes 54.01 (1195 × 1.13% × 4), almacen 28.68, iva (interes+almacen)×16%
        VencimientoResponse s4 = filas.get(3);
        assertCercano(s4.getInteres(),   "54.01",    "1493 S4 interes");
        assertCercano(s4.getAlmacen(),   "28.68",    "1493 S4 almacen");
    }

    @Test
    void calcularAmortizacion_columnasMonotonas_crecenFilaAFila() {
        // D-05: cada columna (interes, almacen, totalInteres, iva, desempeno) debe crecer o
        // mantenerse entre filas; nunca disminuir. Reproduce el síntoma visual que vio Jorge
        // (filas extemporáneas con almacén "menor" que la fila anterior) para filas normales.
        Contrato contrato = fixtureContratoSemanal(new BigDecimal("575.08"));
        when(repository.findById(99L)).thenReturn(Optional.of(contrato));

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("1.55"));
        parametro.setPorcAlmacen(new BigDecimal("0.6"));
        parametro.setPorcGastosAdmin(new BigDecimal("0"));
        parametro.setPorcSancionSemanal(new BigDecimal("2"));
        parametro.setAplicarSancionPorPeriodo(true);
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(parametro));

        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(99L);
        for (int i = 1; i < filas.size(); i++) {
            VencimientoResponse prev = filas.get(i - 1);
            VencimientoResponse cur  = filas.get(i);
            assertThat(cur.getInteres())
                    .as("Fila %d interes >= fila %d", i + 1, i).isGreaterThanOrEqualTo(prev.getInteres());
            assertThat(cur.getAlmacen())
                    .as("Fila %d almacen >= fila %d", i + 1, i).isGreaterThanOrEqualTo(prev.getAlmacen());
            assertThat(cur.getTotalInteres())
                    .as("Fila %d totalInteres >= fila %d", i + 1, i).isGreaterThanOrEqualTo(prev.getTotalInteres());
            assertThat(cur.getIva())
                    .as("Fila %d iva >= fila %d", i + 1, i).isGreaterThanOrEqualTo(prev.getIva());
            assertThat(cur.getDesempeno())
                    .as("Fila %d desempeno >= fila %d", i + 1, i).isGreaterThanOrEqualTo(prev.getDesempeno());
        }
    }

    // =========================================================================
    // D-06: Recalculo server-side al cambiar de plazo y validacion de las tres
    // invariantes (prestamo<=max, avaluoContrato=prestamo*(1+inc), avaluoContrato<=avaluoReal).
    // =========================================================================

    /** Parametro para ALHAJA con % de prestamo s/avaluo y % incremento sobre avaluo (real). */
    private PlazoParametro buildParametroAlhaja(BigDecimal porcPrestamoSAvaluo,
                                                 BigDecimal porcPrestamoSAvaluoReal) {
        PlazoParametro p = new PlazoParametro();
        p.setPorcPrestamoSAvaluo(porcPrestamoSAvaluo);
        p.setUsaAvaluoReal(porcPrestamoSAvaluoReal != null
                && porcPrestamoSAvaluoReal.compareTo(BigDecimal.ZERO) > 0);
        p.setPorcPrestamoSAvaluoReal(porcPrestamoSAvaluoReal);
        return p;
    }

    private void stubPlazo(Long idPlazo, int diasPorPeriodo, int numeroPeriodos) {
        Plazo plazo = new Plazo();
        plazo.setId(idPlazo);
        plazo.setDiasPorPeriodo(diasPorPeriodo);
        plazo.setNumeroPeriodos(numeroPeriodos);
        lenient().when(plazoRepository.findById(idPlazo)).thenReturn(Optional.of(plazo));
    }

    /** Celda 18K/N: plazo A (4 sem) con precioPrestamo P1; plazo B (3 quincenas) con precioPrestamo P2. */
    private void stubTablaAlhaja18K(Integer plazoId, String precioPrestamo) {
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setId(new PlazoHechuraAlhajaId(plazoId, 1, 18, "N"));
        tabla.setPrecioPrestamo(new BigDecimal(precioPrestamo));
        lenient().when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(plazoId, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
    }

    @Test
    void recalcularPartidas_cambioDePlazoDe4SemanasA3Quincenas_unaPartida_recalculaTodosLosValores() {
        // Given: cadena 18K, 5 g netos, hechura N.
        //   Plazo A (4 sem, id=1): precio préstamo = 1,000/g, %Prestamo s/Avaluo = 70, inc = 25
        //     -> avaluoReal = 5,000, prestamoMax = 3,500, prestamo pedido = 3,500, avaluoContrato = 4,375
        //   Plazo B (3 quincenas, id=2): precio préstamo = 900/g, %Prestamo s/Avaluo = 60, inc = 10
        //     -> avaluoReal = 4,500, prestamoMax = 2,700, prestamo CLAMPED a 2,700, avaluoContrato = 2,970
        // El valor pegado del plazo anterior (3,500) debe ajustarse hacia abajo.
        stubPlazo(2L, 15, 3);
        stubTablaAlhaja18K(2, "900.0000");
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(2L, 1, 1))
                .thenReturn(Optional.of(buildParametroAlhaja(new BigDecimal("60"), new BigDecimal("10"))));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenAnswer(inv -> {
                    BigDecimal prestamo = inv.getArgument(0);
                    PlazoParametro p = inv.getArgument(1);
                    if (!Boolean.TRUE.equals(p.getUsaAvaluoReal())
                            || p.getPorcPrestamoSAvaluoReal() == null
                            || p.getPorcPrestamoSAvaluoReal().signum() == 0) {
                        return prestamo;
                    }
                    return prestamo.multiply(BigDecimal.ONE.add(
                            p.getPorcPrestamoSAvaluoReal().divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP)))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                });

        RecalcularPartidasRequest req = new RecalcularPartidasRequest();
        req.setIdPlazo(2L);
        PartidaContratoRequest pr = buildPartidaAlhaja(
                new BigDecimal("5000.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("3500.00"));  // valor stale del plazo A
        req.setPartidas(List.of(pr));

        List<PartidaRecalculadaResponse> out = contratoService.recalcularPartidas(req);

        assertThat(out).hasSize(1);
        PartidaRecalculadaResponse row = out.get(0);
        assertThat(row.getAvaluoReal()).isEqualByComparingTo("4500.00");       // 5 x 900
        assertThat(row.getPrestamoMaximo()).isEqualByComparingTo("2700.00");   // 4500 x 60%
        // Clamp hacia abajo + redondeo a $5
        assertThat(row.getMontoPrestamo()).isEqualByComparingTo("2700.00");
        assertThat(row.getAvaluoContrato()).isEqualByComparingTo("2970.00");   // 2700 x 1.10
    }

    @Test
    void recalcularPartidas_cambioDePlazoDe4SemanasA3Quincenas_dosPartidas_redondeoAUltimaAlhaja() {
        // Given: dos cadenas 18K, 5g y 3g netos. Plazo B: 900/g, 60% s/avaluo.
        //   Partida 1: avaluoReal=4500, max=2700, prestamo pedido=2700 -> stays 2700
        //   Partida 2: avaluoReal=2700, max=1620, prestamo pedido=1620 -> stays 1620 (clamp si piden mas)
        //   Total alhajas = 4320, redondeado a $5 = 4320 (ya es múltiplo); forzar un no-múltiplo:
        //   Pedir p2=1622: clamp a max=1620 => total 4320 (múltiplo). Para probar el redondeo
        //   usamos un max que no caiga en múltiplo de 5. Precio 902/g -> max p1 = 2706, p2 = 1623.6->1623.60.
        //   Total 2706 + 1623.60 = 4329.60; redondeado a 4325; diferencia 4.60 se descuenta de p2.
        stubPlazo(2L, 15, 3);
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("902.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(2, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(2L, 1, 1))
                .thenReturn(Optional.of(buildParametroAlhaja(new BigDecimal("60"), BigDecimal.ZERO)));

        RecalcularPartidasRequest req = new RecalcularPartidasRequest();
        req.setIdPlazo(2L);
        // Préstamos "stale" del plazo anterior, muy por encima de los nuevos máximos.
        PartidaContratoRequest p1 = buildPartidaAlhaja(
                new BigDecimal("1.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("9999.00"));
        PartidaContratoRequest p2 = buildPartidaAlhaja(
                new BigDecimal("1.00"), 18, "N",
                new BigDecimal("3.0000"), new BigDecimal("9999.00"));
        req.setPartidas(List.of(p1, p2));

        List<PartidaRecalculadaResponse> out = contratoService.recalcularPartidas(req);

        assertThat(out).hasSize(2);
        // Clamp a max de cada partida y luego redondeo del total a múltiplo de $5
        // p1 max = 5 x 902 x 60% = 2706.00
        // p2 max = 3 x 902 x 60% = 1623.60
        // total = 4329.60 -> redondeado 4325.00 -> diferencia 4.60 se resta de p2
        assertThat(out.get(0).getMontoPrestamo()).isEqualByComparingTo("2706.00");
        assertThat(out.get(1).getMontoPrestamo()).isEqualByComparingTo("1619.00");
        BigDecimal total = out.get(0).getMontoPrestamo().add(out.get(1).getMontoPrestamo());
        assertThat(total.remainder(new BigDecimal("5"))).isEqualByComparingTo("0");
    }

    @Test
    void crearContrato_plazo3Quincenas_oro_generaContratoYAmortizacionDe3Filas() {
        // Given: Plazo 3 quincenas = 15 x 3; 18K/N 5g, precio 900/g -> avaluoReal 4500,
        // porc_prestamo_s_avaluo = 0 (sin tope extra, mismo semántica que plazo Principal),
        // prestamo = 2700 (múltiplo de 5), inc 10% -> avaluoContrato = 2970.
        stubPlazo(2L, 15, 3);
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("900.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(2, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
        PlazoParametro param = buildParametroAlhaja(BigDecimal.ZERO, new BigDecimal("10"));
        param.setPorcInteres(new BigDecimal("1.00"));
        param.setPorcAlmacen(new BigDecimal("0.6"));
        param.setPorcGastosAdmin(new BigDecimal("18"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(2L, 1, 1))
                .thenReturn(Optional.of(param));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenReturn(new BigDecimal("2970.00"));
        stubPersistencia();

        ContratoRequest req = buildRequestBase();
        req.setIdPlazo(2L);
        req.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("4500.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("2700.00")));

        contratoService.crearContrato(req, "cajero1");

        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getAvaluoReal()).isEqualByComparingTo("4500.00");
        assertThat(guardada.getMontoPrestamo()).isEqualByComparingTo("2700.00");
        assertThat(guardada.getAvaluoContrato()).isEqualByComparingTo("2970.00");
    }

    @Test
    void calcularAmortizacion_plazo3Quincenas_devuelve3FilasEnDias15_30_45() {
        // Verifica que la amortización respeta diasPorPeriodo del plazo (NO hardcodea 7).
        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(alhaja);

        Plazo plazoQuincenal = new Plazo();
        plazoQuincenal.setId(8L);
        plazoQuincenal.setDiasPorPeriodo(15);
        plazoQuincenal.setNumeroPeriodos(3);

        Contrato contrato = new Contrato();
        contrato.setId(500L);
        contrato.setMontoPrestamo(new BigDecimal("2700.00"));
        contrato.setFechaApertura(LocalDateTime.of(2026, 10, 8, 0, 0));
        contrato.setSucursalId(1);
        contrato.setPlazo(plazoQuincenal);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        contrato.setPartidas(partidas);
        when(repository.findById(500L)).thenReturn(Optional.of(contrato));

        PlazoParametro param = new PlazoParametro();
        param.setPorcInteres(new BigDecimal("1.00"));
        param.setPorcAlmacen(new BigDecimal("0.6"));
        param.setPorcGastosAdmin(new BigDecimal("0"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(8L, 1, 1))
                .thenReturn(Optional.of(param));

        List<VencimientoResponse> filas = contratoService.calcularAmortizacion(500L);

        assertThat(filas).hasSize(3);
        assertThat(filas.get(0).getFecha()).isEqualTo(java.time.LocalDate.of(2026, 10, 23)); // +15
        assertThat(filas.get(1).getFecha()).isEqualTo(java.time.LocalDate.of(2026, 11, 7));  // +30
        assertThat(filas.get(2).getFecha()).isEqualTo(java.time.LocalDate.of(2026, 11, 22)); // +45
    }

    @Test
    void crearContrato_invariante1_prestamoSuperaMaximo_rechazaConMensajeDeInvarianteYPartida() {
        // Given: alhaja 18K, 5g, precio préstamo 100/g -> avaluoReal 500, %Prestamo s/Avaluo = 60 ->
        // prestamoMaximo = 300. Cliente pide 400 (como si viniera del plazo anterior).
        // El plazo del setUp es id=1, sin nombre: el mensaje usa "id=1" como fallback.
        Plazo plazoNombrado = new Plazo();
        plazoNombrado.setId(1L);
        plazoNombrado.setNombre("Quincenal - Alahas");
        plazoNombrado.setDiasPorPeriodo(15);
        plazoNombrado.setNumeroPeriodos(3);
        when(plazoRepository.findById(1L)).thenReturn(Optional.of(plazoNombrado));

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(buildParametroAlhaja(new BigDecimal("60"), BigDecimal.ZERO)));

        ContratoRequest req = buildRequestBase();
        req.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("500.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("400.00")));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(req, "cajero1"));
        // El mensaje D-07 debe incluir: partida, prenda, préstamo, máximo, avalúo real, %, plazo.
        assertThat(ex.getMessage()).contains("Partida 1");
        assertThat(ex.getMessage()).contains("ALHAJA");
        assertThat(ex.getMessage()).contains("$400");           // préstamo
        assertThat(ex.getMessage()).contains("$300");           // máximo
        assertThat(ex.getMessage()).contains("$500");           // avalúo real
        assertThat(ex.getMessage()).contains("60%");            // porcentaje
        assertThat(ex.getMessage()).contains("Quincenal");      // nombre del plazo
    }

    @Test
    void crearContrato_invariante2_avaluoContratoNoCoincideConEsperado_rechaza() {
        // Given: alhaja 18K, 5g, precio préstamo 100/g, %Prestamo s/Avaluo = 50, inc = 10.
        // prestamoMaximo = 250, prestamo pedido = 250 ok. avaluoContrato esperado = 275.
        // El cliente envia avaluoContrato = 312.50 (del plazo anterior con inc = 25).
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
        PlazoParametro param = buildParametroAlhaja(new BigDecimal("50"), new BigDecimal("10"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(param));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenReturn(new BigDecimal("275.00"));

        ContratoRequest req = buildRequestBase();
        PartidaContratoRequest pr = buildPartidaAlhaja(
                new BigDecimal("500.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("250.00"));
        pr.setAvaluoContrato(new BigDecimal("312.50"));  // stale del plazo anterior
        req.getPartidas().add(pr);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(req, "cajero1"));
        assertThat(ex.getMessage()).contains("Partida 1");
        assertThat(ex.getMessage()).contains("avalúo de contrato");
        assertThat(ex.getMessage()).contains("312.50");  // enviado
        assertThat(ex.getMessage()).contains("275.00");  // esperado
    }

    @Test
    void crearContrato_invariante3_avaluoContratoSuperaAvaluoReal_rechaza() {
        // Given: alhaja 18K, 5g, precio préstamo 100/g -> avaluoReal = 500. Config inconsistente:
        // %Prestamo s/Avaluo = 90, inc = 25 -> prestamoMax = 450, avaluoContrato = 450 x 1.25 = 562.50
        // que supera el avaluoReal 500. prestamo pedido = 450 (dentro del max), pero invariante 3 falla.
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("100.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 18, "N")))
                .thenReturn(Optional.of(tabla));
        PlazoParametro param = buildParametroAlhaja(new BigDecimal("90"), new BigDecimal("25"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(param));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenReturn(new BigDecimal("562.50"));

        ContratoRequest req = buildRequestBase();
        req.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("500.00"), 18, "N",
                new BigDecimal("5.0000"), new BigDecimal("450.00")));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(req, "cajero1"));
        assertThat(ex.getMessage()).contains("Partida 1");
        assertThat(ex.getMessage()).contains("avalúo de contrato");
        assertThat(ex.getMessage()).contains("supera el avalúo real");
        assertThat(ex.getMessage()).contains("562.50");  // avaluoContrato
        assertThat(ex.getMessage()).contains("500");     // avaluoReal
    }

    @Test
    void crearContrato_alhaja_avaluoRealEsValorDelOro_prestamoDeTablaMasIncrementoPasa() {
        // Caso del 08-oct: Quincenal ALHAJA con % Préstamo s/Avalúo = 0 (= 100%) e incremento 10%.
        // 10K/N, 10 g, precio préstamo 386.58/g -> préstamo de tabla 3,865.80. El valor del oro es
        // 582.31/g (precio base 364.70 / %Préstamo 62.63%) -> avalúo real 5,823.10. Avalúo de
        // contrato = 3,865 × 1.10 = 4,251.50 ≤ 5,823.10: se crea. Antes el avalúo real era el
        // préstamo de tabla (3,865.80) y la invariante 3 lo rechazaba.
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("386.5800"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 10, "N")))
                .thenReturn(Optional.of(tabla));
        when(plazoService.calcularPrecioAvaluoGramo(tabla)).thenReturn(new BigDecimal("582.3100"));
        PlazoParametro param = buildParametroAlhaja(BigDecimal.ZERO, new BigDecimal("10"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(param));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenReturn(new BigDecimal("4251.50"));
        stubPersistencia();

        ContratoRequest req = buildRequestBase();
        req.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("3865.80"), 10, "N",
                new BigDecimal("10.0000"), new BigDecimal("3865.00")));

        contratoService.crearContrato(req, "cajero1");

        PartidaContrato guardada = capturarPartidaGuardada();
        assertThat(guardada.getAvaluoReal()).isEqualByComparingTo("5823.10");
        assertThat(guardada.getMontoPrestamo()).isEqualByComparingTo("3865.00");
        assertThat(guardada.getAvaluoContrato()).isEqualByComparingTo("4251.50");
    }

    @Test
    void crearContrato_alhaja_prestamoMaximoSaleDeLaTabla_noDelValorDelOro() {
        // El avalúo real (valor del oro, 5,823.10) es mayor que el préstamo de tabla (3,865.80);
        // el tope del préstamo sigue siendo la tabla, no el valor del oro.
        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setPrecioPrestamo(new BigDecimal("386.5800"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 10, "N")))
                .thenReturn(Optional.of(tabla));
        when(plazoService.calcularPrecioAvaluoGramo(tabla)).thenReturn(new BigDecimal("582.3100"));
        PlazoParametro param = buildParametroAlhaja(BigDecimal.ZERO, new BigDecimal("10"));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(param));

        ContratoRequest req = buildRequestBase();
        req.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("5823.10"), 10, "N",
                new BigDecimal("10.0000"), new BigDecimal("4000.00")));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(req, "cajero1"));
        assertThat(ex.getMessage()).contains("supera el máximo autorizado $3865.80");
        assertThat(ex.getMessage()).contains("préstamo de tabla $3865.80");
    }

    @Test
    void crearContrato_plata_conIncremento_noAplicaInvariante3() {
        // PLATA: el "avalúo real" es peso × precio de PRÉSTAMO por gramo (10 g × 6.5 = 65.00), así que
        // préstamo 65.00 con incremento 10% -> avalúo de contrato 71.50 > 65.00. No se rechaza.
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaPlata(
                new BigDecimal("65.00"), new BigDecimal("925"),
                new BigDecimal("10.0000"), new BigDecimal("65.00")));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 4, 1))
                .thenReturn(Optional.of(buildParametroPlata(
                        new BigDecimal("6.5"), new BigDecimal("5.0"), BigDecimal.ZERO)));
        when(plazoService.calcularAvaluoContrato(any(BigDecimal.class), any(PlazoParametro.class)))
                .thenReturn(new BigDecimal("71.50"));
        stubPersistencia();

        contratoService.crearContrato(request, "cajero1");

        PartidaContrato partida = capturarPartidaGuardada();
        assertThat(partida.getAvaluoReal()).isEqualByComparingTo("65.00");
        assertThat(partida.getAvaluoContrato()).isEqualByComparingTo("71.50");
    }
}
