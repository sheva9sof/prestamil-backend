package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.PrecioOroRequest;
import com.ignis.prestamil.response.PrecioOroResponse;
import com.ignis.prestamil.service.PlazoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Control GLOBAL del precio del oro. A diferencia de las tablas por plazo,
 * este endpoint vive fuera de los registros de cada plazo: al guardar el precio
 * del gramo de 24K se recalculan automáticamente TODAS las tablas de la sucursal.
 */
@RestController
@RequestMapping("/api/precio-oro")
public class PrecioOroController {

    private final PlazoService plazoService;

    public PrecioOroController(PlazoService plazoService) {
        this.plazoService = plazoService;
    }

    /**
     * Obtiene el precio del oro vigente para la sucursal.
     * GET /api/precio-oro?sucursalId=1
     */
    @GetMapping
    public ResponseEntity<PrecioOroResponse> get(@RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getPrecioOro(sucursalId));
    }

    /**
     * Guarda el precio del oro y recalcula todas las tablas de la sucursal.
     * PUT /api/precio-oro?sucursalId=1
     * body: { "precioGramoBase": 2186.00, "baseKilataje": 21, "calcularSobre": "PRESTAMO" }
     */
    @PutMapping
    public ResponseEntity<PrecioOroResponse> actualizar(
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody PrecioOroRequest request,
            Authentication authentication) {
        String usuario = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(
                plazoService.recalcularTodasLasTablas(sucursalId, request, usuario));
    }
}
