package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.request.MovimientoRequest;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.CotizacionMovimientoResponse;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.MovimientoContratoService;
import com.ignis.prestamil.service.TicketMovimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoContratoController {

    private final MovimientoContratoService movimientoService;
    private final TicketMovimientoService ticketService;

    public MovimientoContratoController(MovimientoContratoService movimientoService,
                                        TicketMovimientoService ticketService) {
        this.movimientoService = movimientoService;
        this.ticketService = ticketService;
    }

    /**
     * Registra un movimiento con cobro (refrendo, finiquito, abono a capital o refrendo parcial). El
     * servidor recalcula los montos; un requestId repetido devuelve el movimiento ya registrado.
     * POST /api/movimientos
     */
    @PostMapping
    public ResponseEntity<MovimientoResponse> registrar(
            @Valid @RequestBody MovimientoRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrar(request, authentication.getName()));
    }

    /**
     * Registra un refrendo sin ventana de Cobro (pago exacto en efectivo).
     * POST /api/movimientos/refrendo
     *
     * @deprecated usar POST /api/movimientos; se conserva mientras algún cliente lo use
     */
    @Deprecated
    @PostMapping("/refrendo")
    public ResponseEntity<MovimientoResponse> refrendar(
            @Valid @RequestBody RefrendoRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.refrendar(request, authentication.getName()));
    }

    /**
     * Cotiza una operación (refrendo, finiquito, abono a capital o refrendo parcial) sin registrarla.
     * POST /api/movimientos/cotizacion
     */
    @PostMapping("/cotizacion")
    public ResponseEntity<CotizacionMovimientoResponse> cotizar(@Valid @RequestBody CotizacionRequest request) {
        return ResponseEntity.ok(movimientoService.cotizar(request));
    }

    /**
     * Cobra la reposición/reimpresión de un contrato y la registra en caja.
     * POST /api/movimientos/reposicion/{contratoId}
     */
    @PostMapping("/reposicion/{contratoId}")
    public ResponseEntity<MovimientoResponse> cobrarReposicion(
            @PathVariable Long contratoId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.cobrarReposicion(contratoId, authentication.getName()));
    }

    /**
     * Nota del movimiento (ticket de ~80 mm) en PDF.
     * GET /api/movimientos/{id}/ticket
     */
    @GetMapping("/{id}/ticket")
    public ResponseEntity<byte[]> ticket(@PathVariable Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ticket-" + id + ".pdf");
        return new ResponseEntity<>(ticketService.generarPdf(id), headers, HttpStatus.OK);
    }

    /**
     * Lista los movimientos de un contrato en orden cronológico.
     * GET /api/movimientos/contrato/{contratoId}
     */
    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<List<MovimientoResponse>> getMovimientos(@PathVariable Long contratoId) {
        return ResponseEntity.ok(movimientoService.getMovimientos(contratoId));
    }
}
