package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.ParametrosSistemaMapper;
import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.request.ParametrosSistemaRequest;
import com.ignis.prestamil.response.ParametrosSistemaResponse;
import com.ignis.prestamil.service.ParametrosSistemaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parametros-sistema")
public class ParametrosSistemaController {

    private final ParametrosSistemaService parametrosSistemaService;
    private final ParametrosSistemaMapper parametrosSistemaMapper;

    public ParametrosSistemaController(ParametrosSistemaService parametrosSistemaService, ParametrosSistemaMapper parametrosSistemaMapper) {
        this.parametrosSistemaService = parametrosSistemaService;
        this.parametrosSistemaMapper = parametrosSistemaMapper;
    }

    @GetMapping
    public ResponseEntity<List<ParametrosSistemaResponse>> findAll() {
        List<ParametrosSistema> parametros = parametrosSistemaService.findAll();
        List<ParametrosSistemaResponse> responses = parametros.stream()
                .map(parametrosSistemaMapper::toParametrosSistemaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParametrosSistemaResponse> findById(@PathVariable Integer id) {
        Optional<ParametrosSistema> parametro = parametrosSistemaService.findById(id);
        return parametro.map(p -> ResponseEntity.ok(parametrosSistemaMapper.toParametrosSistemaResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParametrosSistemaResponse> update(@PathVariable Integer id, @Valid @RequestBody ParametrosSistemaRequest request) {
        if (!parametrosSistemaService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        ParametrosSistemaResponse response = parametrosSistemaService.update(id, request);
        return ResponseEntity.ok(response);
    }

}

