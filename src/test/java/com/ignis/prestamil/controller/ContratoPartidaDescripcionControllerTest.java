package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.TipoPrendaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.BolsaService;
import com.ignis.prestamil.service.CartaResponsivaService;
import com.ignis.prestamil.service.ContratoOperacionService;
import com.ignis.prestamil.service.ContratoPdfService;
import com.ignis.prestamil.service.ContratoService;
import com.ignis.prestamil.service.MovimientoCajaService;
import com.ignis.prestamil.service.PlazoService;
import com.ignis.prestamil.service.TicketBolsaService;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-09: POST /api/contratos rechaza con 400 una partida sin descripción específica suficiente
 * (antifraude). Controlador, ContratoService real y manejo de errores reales; repositorios mock.
 */
@ExtendWith(MockitoExtension.class)
class ContratoPartidaDescripcionControllerTest {

    private static final UsernamePasswordAuthenticationToken VALUADOR =
            new UsernamePasswordAuthenticationToken("valuador1", null, List.of());

    @Mock ContratoRepository contratoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock PlazoRepository plazoRepository;
    @Mock TurnoRepository turnoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock TipoPrendaRepository tipoPrendaRepository;
    @Mock CatValorPrendaRepository catValorPrendaRepository;
    @Mock ContratoMapper contratoMapper;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock PlazoService plazoService;
    @Mock PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;
    @Mock MovimientoContratoRepository movimientoContratoRepository;
    @Mock MovimientoCajaService movimientoCajaService;
    @Mock ContratoPdfService contratoPdfService;
    @Mock ContratoOperacionService contratoOperacionService;
    @Mock BolsaService bolsaService;
    @Mock TicketBolsaService ticketBolsaService;
    @Mock CartaResponsivaService cartaResponsivaService;
    @Mock com.ignis.prestamil.service.MovimientoContratoService movimientoContratoService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ContratoService service = new ContratoService(contratoRepository, clienteRepository, plazoRepository,
                turnoRepository, usuarioRepository, tipoPrendaRepository, catValorPrendaRepository, contratoMapper,
                plazoParametroRepository, plazoService, plazoHechuraAlhajaRepository, parametrosSistemaCache,
                new CalculoContratoService(parametrosSistemaCache), movimientoContratoRepository,
                movimientoCajaService, bolsaService);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ContratoController(service, contratoPdfService, contratoOperacionService,
                                ticketBolsaService, cartaResponsivaService, movimientoContratoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        lenient().when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(new Turno()));
        lenient().when(usuarioRepository.findByNombreUsuario("valuador1")).thenReturn(Optional.of(new Usuario()));
        lenient().when(clienteRepository.findById(1)).thenReturn(Optional.of(new Cliente()));
        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        lenient().when(plazoRepository.findById(1L)).thenReturn(Optional.of(plazo));
        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        lenient().when(tipoPrendaRepository.findById(1)).thenReturn(Optional.of(alhaja));
        TipoPrenda varios = new TipoPrenda();
        varios.setId(3);
        varios.setTipo("VARIOS");
        lenient().when(tipoPrendaRepository.findById(3)).thenReturn(Optional.of(varios));
    }

    @Test
    void crearContrato_partidaSinDescripcionEspecifica_400() throws Exception {
        mockMvc.perform(post("/api/contratos")
                        .principal(VALUADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contrato(partidaAlhaja(""))))
                .andExpect(status().isBadRequest());

        verify(contratoRepository, never()).save(any(Contrato.class));
    }

    @Test
    void crearContrato_descripcionEspecificaMenorA10Caracteres_400() throws Exception {
        // "Esclava" es la descripción genérica que D-09 busca evitar: no identifica la pieza.
        mockMvc.perform(post("/api/contratos")
                        .principal(VALUADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contrato(partidaAlhaja("  Esclava  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("descripción específica")))
                .andExpect(jsonPath("$.message", containsString("10 caracteres")));

        verify(contratoRepository, never()).save(any(Contrato.class));
    }

    @Test
    void crearContrato_variosSinSerieImei_400() throws Exception {
        String partida = """
                {
                  "idTipoPrenda": 3,
                  "descripcion": "Celular negro con funda, pantalla estrellada",
                  "marca": "Apple",
                  "modelo": "iPhone 13",
                  "avaluoReal": 2500.00,
                  "montoPrestamo": 1300.00
                }
                """;

        mockMvc.perform(post("/api/contratos")
                        .principal(VALUADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contrato(partida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("serie/IMEI")));

        verify(contratoRepository, never()).save(any(Contrato.class));
    }

    private static String partidaAlhaja(String descripcion) {
        return """
                {
                  "idTipoPrenda": 1,
                  "descripcion": "%s",
                  "kilataje": 14,
                  "hechura": "N",
                  "pesoNeto": 5.0,
                  "avaluoReal": 1000.00,
                  "montoPrestamo": 500.00
                }
                """.formatted(descripcion);
    }

    private static String contrato(String partida) {
        return """
                { "idCliente": 1, "idPlazo": 1, "partidas": [ %s ] }
                """.formatted(partida);
    }
}
