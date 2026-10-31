package com.ignis.prestamil.request;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.GlobalExceptionHandler;
import com.ignis.prestamil.util.RespuestaReporte;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros comunes de los reportes (REP-00): fechas por defecto, validación del rango, línea de fechas del
 * encabezado y cómo llega desde los query params a un controlador.
 */
class ReporteFiltroTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 17);

    @Test
    void conDefaults_sinFechas_usaHoyComoUnSoloDia() {
        ReporteFiltro filtro = new ReporteFiltro(null, null, null).conDefaults(HOY);

        assertThat(filtro.desde()).isEqualTo(HOY);
        assertThat(filtro.hasta()).isEqualTo(HOY);
        assertThat(filtro.todosLosRamos()).isTrue();
        assertThat(filtro.textoFechas()).isEqualTo("CON FECHA DEL 17/09/2026");
    }

    @Test
    void conDefaults_soloDesde_esUnSoloDia() {
        ReporteFiltro filtro = new ReporteFiltro(1, LocalDate.of(2026, 9, 1), null).conDefaults(HOY);

        assertThat(filtro.hasta()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(filtro.ramo()).isEqualTo(1);
        assertThat(filtro.todosLosRamos()).isFalse();
    }

    @Test
    void textoFechas_rango() {
        ReporteFiltro filtro = new ReporteFiltro(null, LocalDate.of(2026, 9, 1), HOY).conDefaults(HOY);

        assertThat(filtro.textoFechas()).isEqualTo("CON FECHA DEL 01/09/2026 AL 17/09/2026");
    }

    @Test
    void conDefaults_desdePosteriorAHasta_lanzaBadRequest() {
        ReporteFiltro filtro = new ReporteFiltro(null, HOY, LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> filtro.conDefaults(HOY))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("17/09/2026")
                .hasMessageContaining("01/09/2026");
    }

    @Test
    void seRecibeDeLosQueryParams_yRespondeComoArchivo() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter())
                .build();

        mockMvc.perform(get("/prueba").param("ramo", "2").param("desde", "2026-09-01").param("hasta", "2026-09-17"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"prueba.xlsx\""))
                .andExpect(header().string("Content-Type", RespuestaReporte.XLSX.toString()))
                .andExpect(content().string("2|2026-09-01|2026-09-17"));
        mockMvc.perform(get("/prueba"))
                .andExpect(status().isOk())
                .andExpect(content().string("null|null|null"));
    }

    @RestController
    static class ControladorDePrueba {
        @GetMapping("/prueba")
        ResponseEntity<byte[]> prueba(ReporteFiltro filtro) {
            String eco = filtro.ramo() + "|" + filtro.desde() + "|" + filtro.hasta();
            return RespuestaReporte.xlsx(eco.getBytes(StandardCharsets.UTF_8), "prueba.xlsx");
        }
    }
}
