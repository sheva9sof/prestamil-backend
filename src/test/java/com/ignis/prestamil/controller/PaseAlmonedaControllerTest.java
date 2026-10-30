package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.PaseAlmonedaReporteService;
import com.ignis.prestamil.service.PaseAlmonedaService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.util.Constantes;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OTR-01: cajero y gerente solo ven el pase de almoneda de hoy; Sistemas puede pedir otra fecha.
 * Controlador y servicio reales, repositorios mock: se verifica que la fecha que llega a la consulta
 * (y al nombre del archivo exportado) es la del servidor, aunque el cliente mande otra.
 */
@ExtendWith(MockitoExtension.class)
class PaseAlmonedaControllerTest {

    private static final int SUCURSAL = 1;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);
    private static final LocalDate OTRA_FECHA = LocalDate.of(2026, 10, 6);

    private static final UsernamePasswordAuthenticationToken SISTEMAS =
            new UsernamePasswordAuthenticationToken("sistemas", null, List.of());
    private static final UsernamePasswordAuthenticationToken CAJERO =
            new UsernamePasswordAuthenticationToken("cajero", null, List.of());

    @Mock PaseAlmonedaDetalleRepository detalleRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ConfiguracionRepository configuracionRepository;
    @Mock SucursalService sucursalService;
    @Mock PaseAlmonedaService paseAlmonedaService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(HOY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        PaseAlmonedaReporteService service = new PaseAlmonedaReporteService(detalleRepository,
                sucursalRepository, usuarioRepository, configuracionRepository, clock);
        PaseAlmonedaController controller = new PaseAlmonedaController(service, paseAlmonedaService, sucursalService);
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
        lenient().when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.of(sucursal));
        lenient().when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(usuarioRepository.findByNombreUsuario("sistemas")).thenReturn(Optional.of(usuarioConRol(1)));
        lenient().when(usuarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(usuarioConRol(3)));
        Configuracion roles = new Configuracion();
        roles.setValorCadena("1");
        lenient().when(configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA))
                .thenReturn(Optional.of(roles));
    }

    @Test
    void resultados_cajeroPideOtraFecha_recibeHoyYSinSelector() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/resultados").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(HOY.toString()))
                .andExpect(jsonPath("$.puedeElegirFecha").value(false));

        verify(detalleRepository, never()).findByPaseSucursalIdAndPaseFechaAndTipoCambio(eq(SUCURSAL), eq(OTRA_FECHA), any());
    }

    @Test
    void resultados_ponenAlDiaElPaseAntesDeConsultar() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/resultados").principal(CAJERO))
                .andExpect(status().isOk());

        InOrder orden = inOrder(paseAlmonedaService, detalleRepository);
        orden.verify(paseAlmonedaService).ejecutarPendientes();
        orden.verify(detalleRepository, atLeastOnce()).findByPaseSucursalIdAndPaseFechaAndTipoCambio(any(), any(), any());
    }

    @Test
    void exportaciones_noCorrenElPase() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/cartera-vencida/excel").principal(CAJERO))
                .andExpect(status().isOk());

        verifyNoInteractions(paseAlmonedaService);
    }

    @Test
    void resultados_sistemasPideOtraFecha_recibeEsaFecha() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/resultados").param("fecha", OTRA_FECHA.toString()).principal(SISTEMAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(OTRA_FECHA.toString()))
                .andExpect(jsonPath("$.puedeElegirFecha").value(true));
    }

    @Test
    void resultados_sinFecha_usaHoy() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/resultados").principal(SISTEMAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(HOY.toString()));
    }

    @Test
    void exportaciones_cajeroPideOtraFecha_exportaHoy() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/cartera-vencida/excel").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cartera-vencida-" + HOY + ".csv")));
        mockMvc.perform(get("/api/pase-almoneda/pase-a-venta/excel").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("pase-a-venta-" + HOY + ".csv")));
        mockMvc.perform(get("/api/pase-almoneda/cartera-vencida/pdf").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cartera-vencida-" + HOY + ".pdf")));
        mockMvc.perform(get("/api/pase-almoneda/pase-a-venta/pdf").param("fecha", OTRA_FECHA.toString()).principal(CAJERO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("pase-a-venta-" + HOY + ".pdf")));

        verify(detalleRepository, atLeastOnce()).findByPaseSucursalIdAndPaseFechaAndTipoCambio(eq(SUCURSAL), eq(HOY), any());
        verify(detalleRepository, never()).findByPaseSucursalIdAndPaseFechaAndTipoCambio(eq(SUCURSAL), eq(OTRA_FECHA), any());
    }

    @Test
    void exportaciones_sistemasPideOtraFecha_exportaEsaFecha() throws Exception {
        mockMvc.perform(get("/api/pase-almoneda/cartera-vencida/excel").param("fecha", OTRA_FECHA.toString()).principal(SISTEMAS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cartera-vencida-" + OTRA_FECHA + ".csv")));
        mockMvc.perform(get("/api/pase-almoneda/pase-a-venta/pdf").param("fecha", OTRA_FECHA.toString()).principal(SISTEMAS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("pase-a-venta-" + OTRA_FECHA + ".pdf")));

        verify(detalleRepository, never()).findByPaseSucursalIdAndPaseFechaAndTipoCambio(eq(SUCURSAL), eq(HOY), any());
    }

    private static Usuario usuarioConRol(int rolId) {
        Rol rol = new Rol();
        rol.setId(rolId);
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
