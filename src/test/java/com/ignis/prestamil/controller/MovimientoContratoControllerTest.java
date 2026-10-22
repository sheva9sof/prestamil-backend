package com.ignis.prestamil.controller;

import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.response.CotizacionMovimientoResponse;
import com.ignis.prestamil.service.MovimientoContratoService;
import com.ignis.prestamil.service.TicketMovimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MovimientoContratoControllerTest {

    @Mock
    MovimientoContratoService movimientoService;

    @Mock
    TicketMovimientoService ticketService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MovimientoContratoController(movimientoService, ticketService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(), new ByteArrayHttpMessageConverter())
                .build();
    }

    @Test
    void getTicket_devuelveElPdfInline() throws Exception {
        byte[] pdf = "%PDF-1.4 ticket".getBytes();
        when(ticketService.generarPdf(7L)).thenReturn(pdf);

        mockMvc.perform(get("/api/movimientos/7/ticket"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=ticket-7.pdf"))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void getTicketVigente_devuelveElPdfDelUltimoNoCancelado() throws Exception {
        byte[] pdf = "%PDF-1.4 vigente".getBytes();
        when(ticketService.generarPdfVigente(42L)).thenReturn(pdf);

        mockMvc.perform(get("/api/movimientos/contrato/42/ticket-vigente"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=ticket-vigente-42.pdf"))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void postCotizacion_devuelve200ConLaCotizacion() throws Exception {
        CotizacionMovimientoResponse resp = new CotizacionMovimientoResponse();
        resp.setContratoId(42L);
        resp.setTipoMovimiento(TipoMovimiento.RF);
        resp.setTotal(new BigDecimal("95.92"));
        when(movimientoService.cotizar(any(CotizacionRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/movimientos/cotizacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contratoId\":42,\"tipoOperacion\":\"REFRENDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratoId").value(42))
                .andExpect(jsonPath("$.tipoMovimiento").value("RF"))
                .andExpect(jsonPath("$.total").value(95.92));
    }

    @Test
    void postCotizacion_sinTipoOperacion_400() throws Exception {
        mockMvc.perform(post("/api/movimientos/cotizacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contratoId\":42}"))
                .andExpect(status().isBadRequest());

        verify(movimientoService, never()).cotizar(any());
    }
}
