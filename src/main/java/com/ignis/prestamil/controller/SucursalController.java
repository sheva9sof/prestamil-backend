package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.SucursalMapper;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.response.SucursalResponse;
import com.ignis.prestamil.service.SucursalService;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<SucursalResponse> create(@RequestBody Sucursal sucursal) {
        Sucursal savedSucursal = sucursalService.save(sucursal);
        return ResponseEntity.status(HttpStatus.CREATED).body(sucursalMapper.toSucursalResponse(savedSucursal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SucursalResponse> update(@PathVariable Integer id, @RequestBody Sucursal sucursal) {
        if (!sucursalService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        sucursal.setId(id);
        Sucursal updatedSucursal = sucursalService.update(sucursal);
        return ResponseEntity.ok(sucursalMapper.toSucursalResponse(updatedSucursal));
    }

}

