package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.TipoPrendaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.PartidaContratoRequest;
import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.VencimientoResponse;
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

    ContratoService contratoService;

    @BeforeEach
    void setUp() {
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
                plazoHechuraAlhajaRepository
        );

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
                                                       String hechura, BigDecimal pesoGramos,
                                                       BigDecimal montoPrestamo) {
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(1);
        pr.setDescripcion("Anillo de oro");
        pr.setKilataje(kilataje);
        pr.setHechura(hechura);
        pr.setPesoGramos(pesoGramos);
        pr.setAvaluoReal(avaluoRealSpoofed);
        pr.setMontoPrestamo(montoPrestamo);
        return pr;
    }

    /** Partida de plata: el avaluoReal enviado es deliberadamente "spoofed" (el servidor debe ignorarlo). */
    private PartidaContratoRequest buildPartidaPlata(BigDecimal avaluoRealSpoofed, BigDecimal ley,
                                                     BigDecimal pesoGramos, BigDecimal montoPrestamo) {
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(4);
        pr.setDescripcion("Pulsera de plata");
        pr.setLey(ley);
        pr.setPesoGramos(pesoGramos);
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

    /** Stubs necesarios para que crearContrato llegue hasta repository.save sin NPE. */
    private void stubGuardadoExitoso() {
        when(contratoMapper.toResponse(any(Contrato.class))).thenAnswer(inv -> new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato c = inv.getArgument(0);
            if (c.getId() == null) { c.setId(1L); }
            return c;
        });
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

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

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
}
