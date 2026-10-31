package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.response.ResultadosPaseAlmonedaResponse;
import com.ignis.prestamil.service.PaseAlmonedaReporteService;
import com.ignis.prestamil.service.PaseAlmonedaService;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.util.RespuestaReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Pantalla "Resultados del pase de almoneda" (C-11). La ejecucion del pase es automatica
 * ({@link PaseAlmonedaService#ejecutarPendientes()}); aqui se consultan y exportan los resultados.
 *
 * <p>OTR-01: solo los roles de {@code ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA} (Sistemas) pueden
 * pedir otra fecha. Para el resto, todas las rutas ignoran {@code fecha} y usan la de hoy del
 * servidor, tambien en las exportaciones.</p>
 *
 * <p>Consultar resultados pone al dia el pase antes de leerlo, por si ningun otro disparador
 * (arranque, 00:05, abrir turno) ha corrido hoy.</p>
 */
@RestController
@RequestMapping("/api/pase-almoneda")
@RequiredArgsConstructor
public class PaseAlmonedaController {

    private final PaseAlmonedaReporteService reporteService;
    private final PaseAlmonedaService paseAlmonedaService;
    private final SucursalService sucursalService;

    /** Resultados de un pase: cartera vencida + pase a venta. Si no corrio ese dia: listas vacias. */
    @GetMapping("/resultados")
    public ResponseEntity<ResultadosPaseAlmonedaResponse> resultados(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        paseAlmonedaService.ejecutarPendientes();
        return ResponseEntity.ok(reporteService.obtener(sucursalId(), fecha, authentication.getName()));
    }

    /** Exportacion CSV (UTF-8 BOM) de la pestaña "Cartera vencida". Excel lo abre nativo. */
    @GetMapping(value = "/cartera-vencida/excel", produces = "text/csv")
    public ResponseEntity<byte[]> exportarCarteraVencida(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        LocalDate fechaPase = reporteService.fechaPermitida(authentication.getName(), fecha);
        byte[] body = reporteService.exportarCarteraVencidaCsv(sucursalId(), fechaPase);
        return RespuestaReporte.csv(body, "cartera-vencida-" + fechaPase + ".csv");
    }

    /** Exportacion CSV (UTF-8 BOM) de la pestaña "Pase a venta". */
    @GetMapping(value = "/pase-a-venta/excel", produces = "text/csv")
    public ResponseEntity<byte[]> exportarPaseAVenta(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        LocalDate fechaPase = reporteService.fechaPermitida(authentication.getName(), fecha);
        byte[] body = reporteService.exportarPaseAVentaCsv(sucursalId(), fechaPase);
        return RespuestaReporte.csv(body, "pase-a-venta-" + fechaPase + ".csv");
    }

    /** Reporte PDF de la pestaña "Cartera vencida" (reemplaza la impresion del navegador). */
    @GetMapping(value = "/cartera-vencida/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfCarteraVencida(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        LocalDate fechaPase = reporteService.fechaPermitida(authentication.getName(), fecha);
        byte[] body = reporteService.exportarCarteraVencidaPdf(sucursalId(), fechaPase);
        return RespuestaReporte.pdf(body, "cartera-vencida-" + fechaPase + ".pdf");
    }

    /** Reporte PDF de la pestaña "Pase a venta". */
    @GetMapping(value = "/pase-a-venta/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfPaseAVenta(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        LocalDate fechaPase = reporteService.fechaPermitida(authentication.getName(), fecha);
        byte[] body = reporteService.exportarPaseAVentaPdf(sucursalId(), fechaPase);
        return RespuestaReporte.pdf(body, "pase-a-venta-" + fechaPase + ".pdf");
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }
}
