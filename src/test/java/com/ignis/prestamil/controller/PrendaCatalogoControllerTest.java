package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.mapper.PrendaMapper;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.PartidaContratoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.service.CatSubtipoPrendaService;
import com.ignis.prestamil.service.CatValorPrendaService;
import com.ignis.prestamil.service.TipoPrendaService;
import com.ignis.prestamil.util.Constantes;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-09: el catálogo de prendas lo administra solo Sistemas; la clave es obligatoria pero puede
 * repetirse entre prendas con distinta descripción o kilataje (changeset 042). Controlador,
 * servicio real y manejo de errores reales; solo los repositorios son mocks, así que se verifica el
 * código HTTP tal como lo recibe el frontend.
 */
@ExtendWith(MockitoExtension.class)
class PrendaCatalogoControllerTest {

    private static final UsernamePasswordAuthenticationToken SISTEMAS =
            new UsernamePasswordAuthenticationToken("sistemas", null, List.of());
    private static final UsernamePasswordAuthenticationToken GERENTE_SUCURSAL =
            new UsernamePasswordAuthenticationToken("gerente", null, List.of());

    @Mock CatValorPrendaRepository valorRepository;
    @Mock CatSubtipoPrendaRepository subtipoRepository;
    @Mock PartidaContratoRepository partidaContratoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ConfiguracionRepository configuracionRepository;
    @Mock TipoPrendaService tipoPrendaService;
    @Mock CatSubtipoPrendaService catSubtipoPrendaService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CatValorPrendaService service = new CatValorPrendaService(valorRepository, subtipoRepository,
                partidaContratoRepository, usuarioRepository, configuracionRepository);
        PrendaController controller = new PrendaController(
                tipoPrendaService, catSubtipoPrendaService, service, new PrendaMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        lenient().when(usuarioRepository.findByNombreUsuario("sistemas")).thenReturn(Optional.of(usuarioConRol(1)));
        lenient().when(usuarioRepository.findByNombreUsuario("gerente")).thenReturn(Optional.of(usuarioConRol(5)));
        Configuracion roles = new Configuracion();
        roles.setValorCadena("1");
        lenient().when(configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CATALOGO_PRENDAS))
                .thenReturn(Optional.of(roles));

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        CatSubtipoPrenda kilataje = new CatSubtipoPrenda();
        kilataje.setIdAtributo(4);
        kilataje.setTipoPrenda(alhaja);
        lenient().when(subtipoRepository.findById(4)).thenReturn(Optional.of(kilataje));
    }

    @Test
    void postValores_claveRepetidaConOtroKilataje_201() throws Exception {
        // ESC ya existe como "ESCLAVA 14K"; dar de alta ESC "ESCLAVA 10K" es válido (Jorge, 2026-10-07).
        when(valorRepository.save(any(CatValorPrenda.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/prendas/valores")
                        .principal(SISTEMAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ESC", "ESCLAVA 10K").replace("\"kilataje\": 14", "\"kilataje\": 10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").value("ESC"))
                .andExpect(jsonPath("$.kilataje").value(10));
    }

    @Test
    void postValores_sinClave_400() throws Exception {
        mockMvc.perform(post("/api/prendas/valores")
                        .principal(SISTEMAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "ESCLAVA 14K")))
                .andExpect(status().isBadRequest());

        verify(valorRepository, never()).save(any());
    }

    @Test
    void postValores_sinDescripcionGeneral_400() throws Exception {
        mockMvc.perform(post("/api/prendas/valores")
                        .principal(SISTEMAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ESC14", "")))
                .andExpect(status().isBadRequest());

        verify(valorRepository, never()).save(any());
    }

    @Test
    void postValores_sistemas_201() throws Exception {
        when(valorRepository.save(any(CatValorPrenda.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/prendas/valores")
                        .principal(SISTEMAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ESC14", "ESCLAVA 14K")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").value("ESC14"))
                .andExpect(jsonPath("$.descripcion").value("ESCLAVA 14K"));
    }

    @Test
    void putValores_sucursalIntentaEditarCatalogo_403() throws Exception {
        mockMvc.perform(put("/api/prendas/valores/9")
                        .principal(GERENTE_SUCURSAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ESC14", "ESCLAVA 14K")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Solo Sistemas")));

        verify(valorRepository, never()).save(any());
    }

    @Test
    void postYDeleteValores_sucursal_403() throws Exception {
        mockMvc.perform(post("/api/prendas/valores")
                        .principal(GERENTE_SUCURSAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("SEC", "SECADORA DE CABELLO")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/prendas/valores/9").principal(GERENTE_SUCURSAL))
                .andExpect(status().isForbidden());

        verify(valorRepository, never()).save(any());
        verify(valorRepository, never()).delete(any(CatValorPrenda.class));
    }

    private static String json(String clave, String descripcion) {
        return """
                {
                  "idTipoPrenda": 1,
                  "idAtributo": 4,
                  "clave": "%s",
                  "descripcion": "%s",
                  "kilataje": 14,
                  "contienePiedra": false
                }
                """.formatted(clave, descripcion);
    }

    private static Usuario usuarioConRol(int rolId) {
        Rol rol = new Rol();
        rol.setId(rolId);
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
