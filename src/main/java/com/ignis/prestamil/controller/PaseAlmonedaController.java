package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.response.ResultadosPaseAlmonedaResponse;
import com.ignis.prestamil.service.PaseAlmonedaReporteService;
import com.ignis.prestamil.service.SucursalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Pantalla "Resultados del pase de almoneda" (C-11). Solo lectura: la ejecucion del pase sigue
 * siendo automatica al abrir el primer turno (F11); aqui unicamente se consultan y exportan los
 * resultados.
 */
@RestController
@RequestMapping("/api/pase-almoneda")
@RequiredArgsConstructor
public class PaseAlmonedaController {

    private final PaseAlmonedaReporteService reporteService;
    private final SucursalService sucursalService;

    /** Resultados de un pase: cartera vencida + pase a venta. Si no corrio ese dia: listas vacias. */
    @GetMapping("/resultados")
    public ResponseEntity<ResultadosPaseAlmonedaResponse> resultados(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reporteService.obtener(sucursalId(), fecha));
    }

    /** Exportacion CSV (UTF-8 BOM) de la pestaña "Cartera vencida". Excel lo abre nativo. */
    @GetMapping(value = "/cartera-vencida/excel", produces = "text/csv")
    public ResponseEntity<byte[]> exportarCarteraVencida(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        byte[] body = reporteService.exportarCarteraVencidaCsv(sucursalId(), fecha);
        return csvResponse(body, "cartera-vencida-" + fecha + ".csv");
    }

    /** Exportacion CSV (UTF-8 BOM) de la pestaña "Pase a venta". */
    @GetMapping(value = "/pase-a-venta/excel", produces = "text/csv")
    public ResponseEntity<byte[]> exportarPaseAVenta(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        byte[] body = reporteService.exportarPaseAVentaCsv(sucursalId(), fecha);
        return csvResponse(body, "pase-a-venta-" + fecha + ".csv");
    }

    /** Reporte PDF de la pestaña "Cartera vencida" (reemplaza la impresion del navegador). */
    @GetMapping(value = "/cartera-vencida/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfCarteraVencida(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        byte[] body = reporteService.exportarCarteraVencidaPdf(sucursalId(), fecha);
        return pdfResponse(body, "cartera-vencida-" + fecha + ".pdf");
    }

    /** Reporte PDF de la pestaña "Pase a venta". */
    @GetMapping(value = "/pase-a-venta/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfPaseAVenta(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        byte[] body = reporteService.exportarPaseAVentaPdf(sucursalId(), fecha);
        return pdfResponse(body, "pase-a-venta-" + fecha + ".pdf");
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }

    private ResponseEntity<byte[]> csvResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(body);
    }
}
