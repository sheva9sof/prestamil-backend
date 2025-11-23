package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.EmpresaMapper;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.response.EmpresaResponse;
import com.ignis.prestamil.service.EmpresaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> findById(@PathVariable Integer id) {
        Optional<Empresa> empresa = empresaService.findById(id);
        return empresa.map(e -> ResponseEntity.ok(empresaMapper.toEmpresaResponse(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> create(@RequestBody Empresa empresa) {
        Empresa savedEmpresa = empresaService.save(empresa);
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaMapper.toEmpresaResponse(savedEmpresa));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> update(@PathVariable Integer id, @RequestBody Empresa empresa) {
        if (!empresaService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        empresa.setId(id);
        Empresa updatedEmpresa = empresaService.update(empresa);
        return ResponseEntity.ok(empresaMapper.toEmpresaResponse(updatedEmpresa));
    }

}

