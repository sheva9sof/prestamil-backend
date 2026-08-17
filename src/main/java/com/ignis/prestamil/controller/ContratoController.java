package com.ignis.prestamil.controller;

import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.VencimientoResponse;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.service.ContratoService;
import com.ignis.prestamil.service.ContratoPdfService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private final ContratoService contratoService;
    private final ContratoPdfService contratoPdfService;

    public ContratoController(ContratoService contratoService, ContratoPdfService contratoPdfService) {
        this.contratoService = contratoService;
        this.contratoPdfService = contratoPdfService;
    }

    /**
     * Registra un nuevo contrato de empeño.
     * POST /api/contratos
     */
    @PostMapping
    public ResponseEntity<ContratoResponse> crear(
            @Valid @RequestBody ContratoRequest request,
            Authentication authentication) {
        ContratoResponse response = contratoService.crearContrato(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Obtiene un contrato por su ID.
     * GET /api/contratos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ContratoResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.getById(id));
    }

    /**
     * Obtiene un contrato por su folio.
     * GET /api/contratos/folio/{folio}
     */
    @GetMapping("/folio/{folio}")
    public ResponseEntity<ContratoResponse> findByFolio(@PathVariable String folio) {
        return ResponseEntity.ok(contratoService.getByFolio(folio));
    }

    /**
     * Lista contratos de un cliente ordenados del más reciente al más antiguo.
     * GET /api/clientes/{clienteId}/contratos
     */
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<ContratoResponse>> findByCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(contratoService.getContratosPorCliente(clienteId));
    }

    /**
     * Lista contratos vencidos para gestión de recuperación.
     * GET /api/contratos/vencidos
     */
    @GetMapping("/vencidos")
    public ResponseEntity<List<ContratoResponse>> findVencidos() {
        return ResponseEntity.ok(contratoService.getContratosVencidos());
    }

    /**
     * Tabla de amortización (vencimientos por periodo) calculada al vuelo.
     * GET /api/contratos/{id}/amortizacion
     */
    @GetMapping("/{id}/amortizacion")
    public ResponseEntity<List<VencimientoResponse>> amortizacion(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.calcularAmortizacion(id));
    }

    /**
     * Genera el PDF del contrato de mutuo (para visualización/impresión en el sistema).
     * GET /api/contratos/{id}/pdf
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = contratoPdfService.generarPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=contrato-" + id + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
