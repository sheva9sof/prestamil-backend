package com.ignis.prestamil.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.FolioNota;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.BancoRepository;
import com.ignis.prestamil.repository.BitacoraRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.FolioNotaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.CobroService;
import com.ignis.prestamil.service.MovimientoContratoService;
import com.ignis.prestamil.service.TicketMovimientoService;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/movimientos de punta a punta: controlador, servicio, motor de cálculo, validación de pago y
 * manejo de errores reales; solo los repositorios son mocks. Verifica C2 y C5 tal como los recibe el
 * frontend, y que los errores de validación lleguen como 400 con mensaje (no como 500).
 */
@ExtendWith(MockitoExtension.class)
class MovimientoRegistroControllerTest {

    private static final UsernamePasswordAuthenticationToken CAJERO =
            new UsernamePasswordAuthenticationToken("cajero1", null, List.of());

    @Mock MovimientoContratoRepository movimientoRepository;
    @Mock ContratoRepository contratoRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock TurnoRepository turnoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock FolioNotaRepository folioNotaRepository;
    @Mock BancoRepository bancoRepository;
    @Mock ConfiguracionRepository configuracionRepository;
    @Mock BitacoraRepository bitacoraRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;
    @Mock TicketMovimientoService ticketService;

    @BeforeEach
    void setUp() {
        lenient().when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(new Turno()));
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        lenient().when(usuarioRepository.findByNombreUsuario("cajero1")).thenReturn(Optional.of(usuario));
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        lenient().when(movimientoRepository.save(any(MovimientoContrato.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(movimientoRepository.findByRequestId(any())).thenReturn(Optional.empty());
        FolioNota folio = new FolioNota();
        folio.setSucursalId(1);
        folio.setUltimoFolio(27322);
        lenient().when(folioNotaRepository.findBySucursalId(1)).thenReturn(Optional.of(folio));
        Banco bbva = new Banco();
        bbva.setId(1);
        bbva.setNombre("BBVA");
        bbva.setActivo(true);
        lenient().when(bancoRepository.findById(1)).thenReturn(Optional.of(bbva));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private MockMvc mockMvcEn(LocalDate hoy) {
        ZoneId zona = ZoneId.systemDefault();
        Clock clock = Clock.fixed(hoy.atTime(10, 15).atZone(zona).toInstant(), zona);
        MovimientoContratoService service = new MovimientoContratoService(movimientoRepository, contratoRepository,
                plazoParametroRepository, turnoRepository, usuarioRepository, folioNotaRepository,
                configuracionRepository, bitacoraRepository, new CalculoContratoService(parametrosSistemaCache),
                new CobroService(bancoRepository), clock);
        // Fechas como texto ISO, igual que el ObjectMapper de Spring Boot
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        return MockMvcBuilders.standaloneSetup(new MovimientoContratoController(service, ticketService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    /** Contrato 1493 de COCAE: 04 SEM, saldo 1,195, alhajas 1.13% + 0.60%. */
    private Contrato contrato1493(LocalDate fechaContrato, LocalDate vencimiento) {
        Contrato c = new Contrato();
        c.setId(42L);
        c.setFolio("1493");
        c.setMontoPrestamo(new BigDecimal("1195.00"));
        c.setSaldoCapital(new BigDecimal("1195.00"));
        c.setNumRefrendos(0);
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setSucursalId(1);
        c.setFechaContrato(fechaContrato);
        c.setFechaVencimiento(vencimiento);
        c.setFechaComercializacion(vencimiento.plusDays(15));
        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        c.setPlazo(plazo);
        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(alhaja);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        c.setPartidas(partidas);

        PlazoParametro pp = new PlazoParametro();
        pp.setPorcInteres(new BigDecimal("1.13"));
        pp.setPorcAlmacen(new BigDecimal("0.60"));
        pp.setPorcGastosAdmin(new BigDecimal("1.00"));
        pp.setPorcSancionSemanal(new BigDecimal("2.00"));
        pp.setDiasGraciaSinInteres(2);
        pp.setAplicarSancionPorPeriodo(true);
        pp.setNumMaxRefrendos(0);

        lenient().when(contratoRepository.findWithLockById(42L)).thenReturn(Optional.of(c));
        lenient().when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));
        return c;
    }

    private static String refrendo(String pago, String totalCotizado) {
        return """
                {"contratoId":42,"tipoOperacion":"REFRENDO","requestId":"9f1c2a7e-0000-4000-8000-000000000001",
                 "totalCotizado":%s,"pago":%s}""".formatted(totalCotizado, pago);
    }

    private ResultActions postMovimiento(MockMvc mockMvc, String body) throws Exception {
        return mockMvc.perform(post("/api/movimientos")
                .principal(CAJERO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // =========================================================================
    // C2 y C5
    // =========================================================================

    @Test
    void c2_refrendoNormal_201ConElCobroElCambioYLasFechasNuevas() throws Exception {
        contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13));

        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), refrendo("{\"efectivo\":100}", "95.92"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RF"))
                .andExpect(jsonPath("$.monto").value(95.92))
                .andExpect(jsonPath("$.iva").value(13.23))
                .andExpect(jsonPath("$.importeEfectivo").value(100))
                .andExpect(jsonPath("$.cambioEntregado").value(4.08))
                .andExpect(jsonPath("$.periodosNormales").value(4))
                .andExpect(jsonPath("$.fechaContratoNueva").value("2026-08-13"))
                .andExpect(jsonPath("$.fechaVencNueva").value("2026-09-10"))
                .andExpect(jsonPath("$.folioNota").value(27323))
                .andExpect(jsonPath("$.nombreUsuario").value("cajero1"));
    }

    @Test
    void c5_refrendoEnGracia_201ComoRpg() throws Exception {
        contrato1493(LocalDate.of(2023, 5, 10), LocalDate.of(2023, 6, 7));

        postMovimiento(mockMvcEn(LocalDate.of(2023, 6, 9)), refrendo("{\"efectivo\":95.92}", "95.92"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RPG"))
                .andExpect(jsonPath("$.monto").value(95.92))
                .andExpect(jsonPath("$.diasGraciaUsados").value(2))
                .andExpect(jsonPath("$.fechaContratoNueva").value("2023-06-07"))
                .andExpect(jsonPath("$.fechaVencNueva").value("2023-07-05"));
    }

    // =========================================================================
    // Errores: siempre 400 con mensaje para el cajero
    // =========================================================================

    @Test
    void tarjetaMayorAlTotal_400() throws Exception {
        contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13));
        String pago = """
                {"efectivo":0,"tarjeta":100,"tipoTarjeta":"CREDITO","tarjetaUltimos4":"4321",
                 "bancoEmisorId":1,"autorizacion":"778899"}""";

        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), refrendo(pago, "95.92"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tarjeta")));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void sinTurnoActivo_400() throws Exception {
        contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13));
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.empty());

        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), refrendo("{\"efectivo\":100}", "95.92"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("turno activo")));
    }

    @Test
    void sinFormaDePago_400PorValidacion() throws Exception {
        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), """
                {"contratoId":42,"tipoOperacion":"REFRENDO","requestId":"abc"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("forma de pago")));
    }

    @Test
    void numeroDeTarjetaCompleto_400_soloSeAceptanLosUltimos4() throws Exception {
        String pago = """
                {"tarjeta":95.92,"tipoTarjeta":"DEBITO","tarjetaUltimos4":"4111111111111111",
                 "bancoEmisorId":1,"autorizacion":"1"}""";

        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), refrendo(pago, "95.92"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("últimos 4")));
    }

    @Test
    void operacionInexistente_400() throws Exception {
        postMovimiento(mockMvcEn(LocalDate.of(2026, 8, 11)), """
                {"contratoId":42,"tipoOperacion":"REGALAR","requestId":"abc","pago":{"efectivo":1}}""")
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Idempotencia por HTTP
    // =========================================================================

    @Test
    void mismoRequestIdDosVeces_devuelveElMismoMovimiento() throws Exception {
        contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13));
        MockMvc mockMvc = mockMvcEn(LocalDate.of(2026, 8, 11));
        String body = refrendo("{\"efectivo\":100}", "95.92");

        postMovimiento(mockMvc, body).andExpect(status().isCreated());
        ArgumentCaptor<MovimientoContrato> captor = ArgumentCaptor.forClass(MovimientoContrato.class);
        verify(movimientoRepository).save(captor.capture());
        when(movimientoRepository.findByRequestId("9f1c2a7e-0000-4000-8000-000000000001"))
                .thenReturn(Optional.of(captor.getValue()));

        postMovimiento(mockMvc, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folioNota").value(27323))
                .andExpect(jsonPath("$.monto").value(95.92));
        verify(movimientoRepository, times(1)).save(any());
    }
}
