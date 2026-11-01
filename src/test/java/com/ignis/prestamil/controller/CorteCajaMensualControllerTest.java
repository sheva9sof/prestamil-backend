package com.ignis.prestamil.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.CierreCajaService;
import com.ignis.prestamil.service.CorteCajaMensualReporteService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranque;
import com.ignis.prestamil.service.calculo.SaldoArranqueProvider;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
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
 * REP-06: el mensual lo sacan gerente y Sistemas; el cajero recibe 403 en los tres endpoints. Controlador y
 * servicio del reporte reales; el motor del corte es mock: las cifras se prueban en
 * {@code CorteCajaMensualReporteServiceIT}. Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
 */
@ExtendWith(MockitoExtension.class)
class CorteCajaMensualControllerTest {

    private static final int SUCURSAL = 1;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    private static final LocalDate ARRANQUE = LocalDate.of(2026, 10, 1);

    private static final UsernamePasswordAuthenticationToken CAJERO =
            new UsernamePasswordAuthenticationToken("cajero", null, List.of());
    private static final UsernamePasswordAuthenticationToken GERENTE =
            new UsernamePasswordAuthenticationToken("gerente", null, List.of());
    private static final UsernamePasswordAuthenticationToken SISTEMAS =
            new UsernamePasswordAuthenticationToken("sistemas", null, List.of());

    @Mock CorteCajaService corteCajaService;
    @Mock CierreCajaService cierreCajaService;
    @Mock SaldoArranqueProvider saldoArranqueProvider;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ConfiguracionRepository configuracionRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock SucursalService sucursalService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(HOY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        CorteCajaMensualReporteService service = new CorteCajaMensualReporteService(corteCajaService,
                cierreCajaService, saldoArranqueProvider, usuarioRepository, configuracionRepository,
                sucursalRepository, clock);
        CorteCajaMensualController controller = new CorteCajaMensualController(service, sucursalService);
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
        lenient().when(usuarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(usuarioConRol(3)));
        lenient().when(usuarioRepository.findByNombreUsuario("gerente")).thenReturn(Optional.of(usuarioConRol(5)));
        lenient().when(usuarioRepository.findByNombreUsuario("sistemas")).thenReturn(Optional.of(usuarioConRol(1)));
        Configuracion roles = new Configuracion();
        roles.setValorCadena("5,1");
        lenient().when(configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL))
                .thenReturn(Optional.of(roles));
        lenient().when(saldoArranqueProvider.saldoArranque(SUCURSAL))
                .thenReturn(Optional.of(new SaldoArranque(ARRANQUE, new BigDecimal("10000.00"))));
        lenient().when(cierreCajaService.diasPendientes(eq(SUCURSAL), any(), any())).thenReturn(Set.of());
        lenient().when(corteCajaService.calcularRango(eq(SUCURSAL), any(), any()))
                .thenAnswer(inv -> List.of(dia(inv.getArgument(1))));
    }

    @Test
    void cajero_403EnLosTresEndpoints() throws Exception {
        mockMvc.perform(get("/api/caja/corte-mensual/pdf")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31").principal(CAJERO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("gerente y Sistemas")));
        mockMvc.perform(get("/api/caja/corte-mensual/excel")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31").principal(CAJERO))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/caja/corte-mensual/contexto").principal(CAJERO))
                .andExpect(status().isForbidden());

        verify(corteCajaService, never()).calcularRango(any(), any(), any());
    }

    @Test
    void gerente_pdfDeOctubre() throws Exception {
        mockMvc.perform(get("/api/caja/corte-mensual/pdf")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31").principal(GERENTE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"corte-caja-mensual-2026-10.pdf\""));

        // El mes en curso llega hasta hoy (aquí hoy es el último día del mes)
        verify(corteCajaService).calcularRango(SUCURSAL, LocalDate.of(2026, 10, 1), HOY);
    }

    @Test
    void sistemas_excelDeOctubre() throws Exception {
        mockMvc.perform(get("/api/caja/corte-mensual/excel")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31").principal(SISTEMAS))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"corte-caja-mensual-2026-10.xlsx\""));
    }

    @Test
    void gerente_contexto() throws Exception {
        mockMvc.perform(get("/api/caja/corte-mensual/contexto").principal(GERENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value(HOY.toString()))
                .andExpect(jsonPath("$.fechaArranque").value(ARRANQUE.toString()));
    }

    @Test
    void gerente_ramoOMedioMes_400() throws Exception {
        mockMvc.perform(get("/api/caja/corte-mensual/pdf")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31").param("ramo", "1")
                        .principal(GERENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ramo")));
        mockMvc.perform(get("/api/caja/corte-mensual/excel")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-15").principal(GERENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("mes completo")));

        verify(corteCajaService, never()).calcularRango(any(), any(), any());
    }

    /** Un día con cifras sencillas: el contenido no importa aquí. */
    private static CorteCajaResultado dia(LocalDate fecha) {
        BigDecimal cero = BigDecimal.ZERO.setScale(2);
        BigDecimal saldo = new BigDecimal("10000.00");
        return new CorteCajaResultado(SUCURSAL, fecha, null, List.of(),
                new CorteCajaResultado.Totales(cero, cero), new CorteCajaResultado.Totales(cero, cero),
                saldo, cero, cero, saldo, cero, cero, cero, cero, cero, null, saldo);
    }

    private static Usuario usuarioConRol(int rolId) {
        Rol rol = new Rol();
        rol.setId(rolId);
        Usuario u = new Usuario();
        u.setRol(rol);
        return u;
    }
}
