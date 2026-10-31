package com.ignis.prestamil.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.CorteCajaConsultaService;
import com.ignis.prestamil.service.CorteCajaReporteService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CAJ-07a y REP-04: el cajero solo ve y saca el corte de hoy; gerente y Sistemas pueden pedir otro día
 * (como OTR-01). Controlador, servicio de consulta y reporte reales; el motor del corte es mock: aquí se
 * prueba qué día se calcula y cómo llega al frontend, no las cifras (eso es {@code CorteCajaServiceIT} y
 * {@code CorteCajaReporteServiceIT}).
 */
@ExtendWith(MockitoExtension.class)
class CorteCajaControllerTest {

    private static final int SUCURSAL = 1;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    private static final LocalDate OTRA_FECHA = LocalDate.of(2026, 10, 30);

    private static final UsernamePasswordAuthenticationToken CAJERO =
            new UsernamePasswordAuthenticationToken("cajero", null, List.of());
    private static final UsernamePasswordAuthenticationToken GERENTE =
            new UsernamePasswordAuthenticationToken("gerente", null, List.of());

    @Mock CorteCajaService corteCajaService;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ConfiguracionRepository configuracionRepository;
    @Mock SucursalService sucursalService;
    @Mock SucursalRepository sucursalRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(HOY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        CorteCajaConsultaService service = new CorteCajaConsultaService(corteCajaService, usuarioRepository,
                configuracionRepository, clock);
        CorteCajaReporteService reporte = new CorteCajaReporteService(service, sucursalRepository, clock);
        CorteCajaController controller = new CorteCajaController(service, reporte, sucursalService);
        // Mismo ObjectMapper que Spring Boot: fechas como "yyyy-MM-dd", no como arreglo.
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter(), json)
                .build();

        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        lenient().when(sucursalService.findUnique()).thenReturn(Optional.of(sucursal));
        lenient().when(usuarioRepository.findByNombreUsuario("gerente")).thenReturn(Optional.of(usuarioConRol(5)));
        lenient().when(usuarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(usuarioConRol(3)));
        Configuracion roles = new Configuracion();
        roles.setValorCadena("5,1");
        lenient().when(configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA))
                .thenReturn(Optional.of(roles));
        lenient().when(corteCajaService.calcular(eq(SUCURSAL), any()))
                .thenAnswer(inv -> corteConCifras(inv.getArgument(1)));
    }

    @Test
    void corte_cajeroPideOtraFecha_recibeHoyYSinSelector() throws Exception {
        mockMvc.perform(get("/api/caja/corte").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(HOY.toString()))
                .andExpect(jsonPath("$.hoy").value(HOY.toString()))
                .andExpect(jsonPath("$.puedeElegirFecha").value(false));

        verify(corteCajaService).calcular(SUCURSAL, HOY);
        verify(corteCajaService, never()).calcular(SUCURSAL, OTRA_FECHA);
    }

    @Test
    void corte_gerentePideOtraFecha_recibeEsaFecha() throws Exception {
        mockMvc.perform(get("/api/caja/corte").param("fecha", OTRA_FECHA.toString()).principal(GERENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(OTRA_FECHA.toString()))
                .andExpect(jsonPath("$.hoy").value(HOY.toString()))
                .andExpect(jsonPath("$.puedeElegirFecha").value(true));

        verify(corteCajaService).calcular(SUCURSAL, OTRA_FECHA);
    }

    @Test
    void corte_sinFecha_usaHoy() throws Exception {
        mockMvc.perform(get("/api/caja/corte").principal(GERENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(HOY.toString()));
    }

    @Test
    void corte_gerentePideDiaFuturo_400() throws Exception {
        mockMvc.perform(get("/api/caja/corte").param("fecha", HOY.plusDays(1).toString()).principal(GERENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("01/11/2026")));

        verify(corteCajaService, never()).calcular(any(), any());
    }

    @Test
    void corte_conCifras_mandaRenglonesEnOrdenYBloqueInferior() throws Exception {
        mockMvc.perform(get("/api/caja/corte").principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corte.aviso").doesNotExist())
                .andExpect(jsonPath("$.corte.renglones", hasSize(RenglonCorte.values().length)))
                .andExpect(jsonPath("$.corte.renglones[0].renglon").value("DOTACIONES"))
                .andExpect(jsonPath("$.corte.renglones[0].etiqueta").value("DOTACIONES"))
                .andExpect(jsonPath("$.corte.renglones[0].columna").value("INGRESO"))
                .andExpect(jsonPath("$.corte.renglones[1].etiqueta").value("EMPEÑOS"))
                .andExpect(jsonPath("$.corte.renglones[1].efectivo").value(1000.00))
                .andExpect(jsonPath("$.corte.ingresos.tarjeta").value(200.00))
                .andExpect(jsonPath("$.corte.diferencia").value(4000.00))
                .andExpect(jsonPath("$.corte.indicador").value("F"))
                .andExpect(jsonPath("$.corte.saldoFinal").value(5000.00));
    }

    @Test
    void corte_sinArranque_mandaAvisoSinCifras() throws Exception {
        String aviso = "La sucursal 1 no tiene saldo de arranque de caja: Sistemas debe capturarlo antes de hacer cortes o traspasos.";
        lenient().when(corteCajaService.calcular(SUCURSAL, HOY))
                .thenReturn(CorteCajaResultado.sinCifras(SUCURSAL, HOY, aviso));

        mockMvc.perform(get("/api/caja/corte").principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corte.aviso").value(aviso))
                .andExpect(jsonPath("$.corte.renglones", hasSize(0)))
                .andExpect(jsonPath("$.corte.diferencia").doesNotExist())
                .andExpect(jsonPath("$.corte.saldoFinal").doesNotExist());
    }

    @Test
    void pdf_cajeroPideOtraFecha_saleElDeHoy() throws Exception {
        mockMvc.perform(get("/api/caja/corte/pdf").param("desde", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"corte-caja-" + HOY + ".pdf\""));

        verify(corteCajaService).calcular(SUCURSAL, HOY);
        verify(corteCajaService, never()).calcular(SUCURSAL, OTRA_FECHA);
    }

    @Test
    void excel_gerentePideOtraFecha_saleEsaFecha() throws Exception {
        mockMvc.perform(get("/api/caja/corte/excel").param("desde", OTRA_FECHA.toString()).principal(GERENTE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"corte-caja-" + OTRA_FECHA + ".xlsx\""));

        verify(corteCajaService).calcular(SUCURSAL, OTRA_FECHA);
    }

    @Test
    void pdf_sinArranque_200ConElAviso() throws Exception {
        lenient().when(corteCajaService.calcular(SUCURSAL, HOY))
                .thenReturn(CorteCajaResultado.sinCifras(SUCURSAL, HOY, "Sin saldo de arranque."));

        mockMvc.perform(get("/api/caja/corte/pdf").principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void pdf_rango_400() throws Exception {
        mockMvc.perform(get("/api/caja/corte/pdf").principal(GERENTE)
                        .param("desde", OTRA_FECHA.minusDays(1).toString()).param("hasta", OTRA_FECHA.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("un solo día")));

        verify(corteCajaService, never()).calcular(any(), any());
    }

    @Test
    void excel_conRamo_400() throws Exception {
        mockMvc.perform(get("/api/caja/corte/excel").principal(GERENTE).param("ramo", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ramo")));
    }

    @Test
    void pdf_gerentePideDiaFuturo_400() throws Exception {
        mockMvc.perform(get("/api/caja/corte/pdf").param("desde", HOY.plusDays(1).toString()).principal(GERENTE))
                .andExpect(status().isBadRequest());

        verify(corteCajaService, never()).calcular(any(), any());
    }

    /** Dotación de 5,000, un empeño de 1,000 y un refrendo de 200 con tarjeta: quedan 4,000 en la gaveta. */
    private static CorteCajaResultado corteConCifras(LocalDate fecha) {
        BigDecimal cero = new BigDecimal("0.00");
        List<CorteCajaResultado.Renglon> renglones = Arrays.stream(RenglonCorte.values())
                .map(r -> new CorteCajaResultado.Renglon(r, r.getEtiqueta(), r.getColumna(),
                        switch (r) {
                            case DOTACIONES -> new BigDecimal("5000.00");
                            case EMPENOS -> new BigDecimal("1000.00");
                            default -> cero;
                        },
                        r == RenglonCorte.REFRENDOS ? new BigDecimal("200.00") : cero))
                .toList();
        return new CorteCajaResultado(SUCURSAL, fecha, null, renglones,
                new CorteCajaResultado.Totales(new BigDecimal("5000.00"), new BigDecimal("200.00")),
                new CorteCajaResultado.Totales(new BigDecimal("1000.00"), cero),
                new BigDecimal("10000.00"), cero, cero, new BigDecimal("10000.00"),
                new BigDecimal("5000.00"), cero, new BigDecimal("1000.00"), cero,
                new BigDecimal("4000.00"), CorteCajaResultado.Indicador.F, new BigDecimal("5000.00"));
    }

    private static Usuario usuarioConRol(int rolId) {
        Rol rol = new Rol();
        rol.setId(rolId);
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
