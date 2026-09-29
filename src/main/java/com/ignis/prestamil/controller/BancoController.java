package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.BancoMapper;
import com.ignis.prestamil.request.BancoRequest;
import com.ignis.prestamil.response.BancoResponse;
import com.ignis.prestamil.service.BancoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bancos")
public class BancoController {

    private final BancoService bancoService;
    private final BancoMapper bancoMapper;

    public BancoController(BancoService bancoService, BancoMapper bancoMapper) {
        this.bancoService = bancoService;
        this.bancoMapper = bancoMapper;
    }

    /**
     * Lista los bancos emisores; con {@code soloActivos=true} solo los que muestra la ventana de Cobro.
     * GET /api/bancos?soloActivos=
     */
    @GetMapping
    public ResponseEntity<List<BancoResponse>> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(bancoService.listar(soloActivos).stream()
                .map(bancoMapper::toBancoResponse)
                .toList());
    }

    /**
     * Alta de un banco emisor.
     * POST /api/bancos
     */
    @PostMapping
    public ResponseEntity<BancoResponse> crear(@Valid @RequestBody BancoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bancoMapper.toBancoResponse(bancoService.crear(request)));
    }

    /**
     * Edición de nombre y activación/desactivación de un banco (no hay borrado físico).
     * PUT /api/bancos/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<BancoResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody BancoRequest request) {
        return ResponseEntity.ok(bancoMapper.toBancoResponse(bancoService.actualizar(id, request)));
    }
}
