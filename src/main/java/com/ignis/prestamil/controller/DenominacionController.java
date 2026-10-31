package com.ignis.prestamil.controller;

import com.ignis.prestamil.response.DenominacionResponse;
import com.ignis.prestamil.service.TraspasoCajaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo de denominaciones para el desglose de traspasos y el arqueo ({@code denominaciones-modal}).
 */
@RestController
@RequestMapping("/api/caja/denominaciones")
@RequiredArgsConstructor
public class DenominacionController {

    private final TraspasoCajaService traspasoCajaService;

    /**
     * Denominaciones activas, de mayor a menor (Q-10: sin 0.01, con 0.05).
     * GET /api/caja/denominaciones
     */
    @GetMapping
    public ResponseEntity<List<DenominacionResponse>> activas() {
        return ResponseEntity.ok(traspasoCajaService.denominacionesActivas());
    }
}
