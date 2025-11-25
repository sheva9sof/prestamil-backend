package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.SucursalMapper;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.request.SucursalRequest;
import com.ignis.prestamil.response.SucursalResponse;
import com.ignis.prestamil.service.SucursalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;


@RestController
@RequestMapping("/api/sucursales")
public class SucursalController {

    private final SucursalService sucursalService;
    private final SucursalMapper sucursalMapper;

    public SucursalController(SucursalService sucursalService, SucursalMapper sucursalMapper) {
        this.sucursalService = sucursalService;
        this.sucursalMapper = sucursalMapper;
    }

    @GetMapping
    public ResponseEntity<SucursalResponse> getSucursal() {
        Optional<Sucursal> sucursal = sucursalService.findUnique();
        return sucursal.map(s -> ResponseEntity.ok(sucursalMapper.toSucursalResponse(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SucursalResponse> update(@PathVariable Integer id, @Valid @RequestBody SucursalRequest request) {
        if (!sucursalService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        SucursalResponse response = sucursalService.update(id, request);
        return ResponseEntity.ok(response);
    }
    
}

