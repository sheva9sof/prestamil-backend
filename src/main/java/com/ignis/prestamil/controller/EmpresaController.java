package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.EmpresaMapper;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.request.EmpresaRequest;
import com.ignis.prestamil.response.EmpresaResponse;
import com.ignis.prestamil.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;
    private final EmpresaMapper empresaMapper;

    public EmpresaController(EmpresaService empresaService, EmpresaMapper empresaMapper) {
        this.empresaService = empresaService;
        this.empresaMapper = empresaMapper;
    }

    @GetMapping
    public ResponseEntity<List<EmpresaResponse>> findAll() {
        List<Empresa> empresas = empresaService.findAll();
        List<EmpresaResponse> responses = empresas.stream()
                .map(empresaMapper::toEmpresaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> create(@Valid @RequestBody EmpresaRequest request) {
        EmpresaResponse response = empresaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> update(@PathVariable Integer id, @Valid @RequestBody EmpresaRequest request) {
        if (!empresaService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        EmpresaResponse response = empresaService.update(id, request);
        return ResponseEntity.ok(response);
    }

}

