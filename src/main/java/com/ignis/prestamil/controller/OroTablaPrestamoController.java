package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.OroCeldaUpdateRequest;
import com.ignis.prestamil.response.OroCeldaResponse;
import com.ignis.prestamil.service.OroTablaPrestamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pantalla "Configuracion del Oro": expone la tabla global de 24 celdas de %Prestamo
 * (8 kilates x 3 hechuras) y permite editar el %Prestamo de una celda (ORO-05, ORO-07).
 */
@RestController
@RequestMapping("/api/oro-tabla-prestamo")
@RequiredArgsConstructor
public class OroTablaPrestamoController {

    private final OroTablaPrestamoService oroTablaPrestamoService;

    /**
     * Lista las 24 celdas de la tabla de %Prestamo con sus precios calculados.
     * GET /api/oro-tabla-prestamo?sucursalId=1
     */
    @GetMapping
    public ResponseEntity<List<OroCeldaResponse>> getTabla(
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(oroTablaPrestamoService.getTabla(sucursalId));
    }

    /**
     * Actualiza el %Prestamo de una celda (kilataje/hechura) y dispara el recalculo en
     * cascada del precioBase en todos los plazos existentes.
     * PUT /api/oro-tabla-prestamo/{kilataje}/{hechura}?sucursalId=1
     */
    @PutMapping("/{kilataje}/{hechura}")
    public ResponseEntity<OroCeldaResponse> actualizarCelda(
            @PathVariable Integer kilataje,
            @PathVariable String hechura,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody OroCeldaUpdateRequest request) {
        return ResponseEntity.ok(oroTablaPrestamoService.actualizarCelda(sucursalId, kilataje, hechura, request));
    }
}
