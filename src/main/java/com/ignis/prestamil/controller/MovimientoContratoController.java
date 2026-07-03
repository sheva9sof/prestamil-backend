package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.MovimientoContratoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoContratoController {

    private final MovimientoContratoService movimientoService;

    public MovimientoContratoController(MovimientoContratoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    /**
     * Registra un refrendo (normal o extemporáneo) sobre un contrato.
     * POST /api/movimientos/refrendo
     */
    @PostMapping("/refrendo")
    public ResponseEntity<MovimientoResponse> refrendar(
            @Valid @RequestBody RefrendoRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.refrendar(request, authentication.getName()));
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
     * Lista los movimientos de un contrato en orden cronológico.
     * GET /api/movimientos/contrato/{contratoId}
     */
    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<List<MovimientoResponse>> getMovimientos(@PathVariable Long contratoId) {
        return ResponseEntity.ok(movimientoService.getMovimientos(contratoId));
    }
}
