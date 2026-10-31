package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.response.CorteCajaResponse;
import com.ignis.prestamil.service.CorteCajaConsultaService;
import com.ignis.prestamil.service.CorteCajaReporteService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.util.RespuestaReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Corte de caja de un día para la pantalla (CAJ-07a), sin cierre, y su reporte en PDF y Excel (REP-04).
 * Las cifras salen de {@code CorteCajaService} (RC-09). El cajero solo ve y saca el de hoy (como OTR-01).
 */
@RestController
@RequestMapping("/api/caja/corte")
@RequiredArgsConstructor
public class CorteCajaController {

    private final CorteCajaConsultaService corteCajaConsultaService;
    private final CorteCajaReporteService corteCajaReporteService;
    private final SucursalService sucursalService;

    /**
     * Corte del día (por defecto hoy). Antes del arranque o sin saldo de arranque responde 200 con el
     * aviso y sin cifras.
     * GET /api/caja/corte?fecha=
     */
    @GetMapping
    public ResponseEntity<CorteCajaResponse> corte(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        return ResponseEntity.ok(corteCajaConsultaService.consultar(sucursalId(), fecha, authentication.getName()));
    }

    /**
     * Corte de caja del día en PDF (REP-04). Un solo día: {@code desde} es el día del corte (sin él, hoy);
     * {@code hasta} distinto o {@code ramo} responden 400. Sin cifras sale el encabezado con el aviso.
     * GET /api/caja/corte/pdf?desde=
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(ReporteFiltro filtro, Authentication authentication) {
        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.pdf(sucursalId(), filtro,
                authentication.getName());
        return RespuestaReporte.pdf(archivo.bytes(), "corte-caja-" + archivo.fecha() + ".pdf");
    }

    /**
     * Corte de caja del día en Excel (.xlsx), mismos filtros que el PDF.
     * GET /api/caja/corte/excel?desde=
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(ReporteFiltro filtro, Authentication authentication) {
        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.xlsx(sucursalId(), filtro,
                authentication.getName());
        return RespuestaReporte.xlsx(archivo.bytes(), "corte-caja-" + archivo.fecha() + ".xlsx");
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }
}
