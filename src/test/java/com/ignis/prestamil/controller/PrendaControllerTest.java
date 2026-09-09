package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.PrendaMapper;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import com.ignis.prestamil.response.CatValorPrendaResponse;
import com.ignis.prestamil.service.CatSubtipoPrendaService;
import com.ignis.prestamil.service.CatValorPrendaService;
import com.ignis.prestamil.service.TipoPrendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PrendaControllerTest {

    @Mock
    TipoPrendaService tipoPrendaService;

    @Mock
    CatSubtipoPrendaService catSubtipoPrendaService;

    @Mock
    CatValorPrendaService catValorPrendaService;

    @Mock
    PrendaMapper prendaMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PrendaController controller = new PrendaController(
                tipoPrendaService,
                catSubtipoPrendaService,
                catValorPrendaService,
                prendaMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void postValores_creaValorDeCatalogo() throws Exception {
        CatValorPrenda entity = mockResponse(25);
        when(catValorPrendaService.create(any(CatValorPrendaRequest.class))).thenReturn(entity);

        mockMvc.perform(post("/api/prendas/valores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idValorAtributo").value(25));
    }

    @Test
    void getValores_sinCategoria_devuelveCatalogoCompleto() throws Exception {
        CatValorPrenda entity = mockResponse(25);
        when(catValorPrendaService.findAllOrdered()).thenReturn(java.util.List.of(entity));

        mockMvc.perform(get("/api/prendas/valores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idValorAtributo").value(25));
    }

    @Test
    void putValores_actualizaValorDeCatalogo() throws Exception {
        CatValorPrenda entity = mockResponse(25);
        when(catValorPrendaService.update(any(Integer.class), any(CatValorPrendaRequest.class)))
                .thenReturn(entity);

        mockMvc.perform(put("/api/prendas/valores/25")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Celular"));
    }

    private CatValorPrenda mockResponse(int id) {
        CatValorPrenda entity = new CatValorPrenda();
        CatValorPrendaResponse response = new CatValorPrendaResponse();
        response.setIdValorAtributo(id);
        response.setDescripcion("Celular");

        when(prendaMapper.toCatValorPrendaResponse(entity)).thenReturn(response);
        return entity;
    }

    private String validRequestJson() {
        return """
                {
                  "idTipoPrenda": 3,
                  "idAtributo": 6,
                  "descripcion": "Celular",
                  "clave": "AB-101",
                  "kilataje": null,
                  "contienePiedad": false
                }
                """;
    }
}
