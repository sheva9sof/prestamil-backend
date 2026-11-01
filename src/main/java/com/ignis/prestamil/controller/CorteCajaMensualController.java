package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.response.CorteCajaMensualContextoResponse;
import com.ignis.prestamil.service.CorteCajaMensualReporteService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.util.RespuestaReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporte mensual de corte de caja en PDF y Excel (REP-06). Las cifras salen de {@code CorteCajaService}
 * (RC-09). Solo gerente y Sistemas ({@code ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL}); el cajero recibe 403.
 */
@RestController
@RequestMapping("/api/caja/corte-mensual")
@RequiredArgsConstructor
public class CorteCajaMensualController {

    private final CorteCajaMensualReporteService corteCajaMensualReporteService;
    private final SucursalService sucursalService;

    /**
     * Hoy del servidor y fecha de arranque de caja, para el selector de mes.
     * GET /api/caja/corte-mensual/contexto
     */
    @GetMapping("/contexto")
    public ResponseEntity<CorteCajaMensualContextoResponse> contexto(Authentication authentication) {
        return ResponseEntity.ok(corteCajaMensualReporteService.contexto(sucursalId(), authentication.getName()));
    }

    /**
     * Mensual en PDF. {@code desde} = día 1 del mes y {@code hasta} = último día (opcional); sin {@code desde},
     * el mes en curso. Un ramo, un rango que no es un mes o un mes futuro responden 400. Si todo el mes es
     * anterior al arranque, sale el encabezado con el aviso.
     * GET /api/caja/corte-mensual/pdf?desde=&hasta=
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(ReporteFiltro filtro, Authentication authentication) {
        CorteCajaMensualReporteService.Archivo archivo = corteCajaMensualReporteService.pdf(sucursalId(), filtro,
                authentication.getName());
        return RespuestaReporte.pdf(archivo.bytes(), "corte-caja-mensual-" + archivo.mes() + ".pdf");
    }

    /**
     * Mensual en Excel (.xlsx), mismos filtros que el PDF.
     * GET /api/caja/corte-mensual/excel?desde=&hasta=
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(ReporteFiltro filtro, Authentication authentication) {
        CorteCajaMensualReporteService.Archivo archivo = corteCajaMensualReporteService.xlsx(sucursalId(), filtro,
                authentication.getName());
        return RespuestaReporte.xlsx(archivo.bytes(), "corte-caja-mensual-" + archivo.mes() + ".xlsx");
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }
}
