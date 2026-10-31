package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.request.CancelarTraspasoCajaRequest;
import com.ignis.prestamil.request.TraspasoCajaRequest;
import com.ignis.prestamil.response.TraspasoCajaContextoResponse;
import com.ignis.prestamil.response.TraspasoCajaResponse;
import com.ignis.prestamil.service.SucursalService;
import com.ignis.prestamil.service.TicketTraspasoCajaService;
import com.ignis.prestamil.service.TraspasoCajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Traspasos de dinero entre cajas (CAJ-02, RC-02): ministración, concentración, dotación y recolección.
 * Las cajas, la fecha de operación, el folio y el importe los pone el servidor.
 */
@RestController
@RequestMapping("/api/caja/traspasos")
@RequiredArgsConstructor
public class TraspasoCajaController {

    private final TraspasoCajaService traspasoCajaService;
    private final TicketTraspasoCajaService ticketService;
    private final SucursalService sucursalService;

    /**
     * Registra un traspaso con su desglose por denominación.
     * POST /api/caja/traspasos
     */
    @PostMapping
    public ResponseEntity<TraspasoCajaResponse> registrar(
            @Valid @RequestBody TraspasoCajaRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(traspasoCajaService.registrar(sucursalId(), request, authentication.getName()));
    }

    /**
     * Traspasos de un día (por defecto hoy), opcionalmente de un solo tipo.
     * GET /api/caja/traspasos?fecha=&tipo=
     */
    @GetMapping
    public ResponseEntity<List<TraspasoCajaResponse>> listar(
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(value = "tipo", required = false) TipoTraspasoCaja tipo) {
        return ResponseEntity.ok(traspasoCajaService.listar(sucursalId(), fecha, tipo));
    }

    /**
     * Cajas que fija el tipo, fecha de operación del servidor y permisos del usuario para la pantalla.
     * GET /api/caja/traspasos/contexto?tipo=
     */
    @GetMapping("/contexto")
    public ResponseEntity<TraspasoCajaContextoResponse> contexto(
            @RequestParam("tipo") TipoTraspasoCaja tipo,
            Authentication authentication) {
        return ResponseEntity.ok(traspasoCajaService.contexto(sucursalId(), tipo, authentication.getName()));
    }

    /**
     * Cancela un traspaso del día.
     * POST /api/caja/traspasos/{id}/cancelar
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<TraspasoCajaResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelarTraspasoCajaRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(traspasoCajaService.cancelar(id, request, authentication.getName()));
    }

    /**
     * Ticket del traspaso (~80 mm) en PDF; un traspaso cancelado sale con "CANCELADO" en grande.
     * GET /api/caja/traspasos/{id}/ticket
     */
    @GetMapping("/{id}/ticket")
    public ResponseEntity<byte[]> ticket(@PathVariable Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ticket-traspaso-" + id + ".pdf");
        return new ResponseEntity<>(ticketService.generarPdf(id), headers, HttpStatus.OK);
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }
}
