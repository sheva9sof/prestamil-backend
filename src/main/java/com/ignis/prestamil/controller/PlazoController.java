package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.mapper.PlazoParametroMapper;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.request.PlazoRequest;
import com.ignis.prestamil.response.PlazoParametroResponse;
import com.ignis.prestamil.response.PlazoResponse;
import com.ignis.prestamil.service.PlazoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/plazos")
public class PlazoController {

    private final PlazoService plazoService;
    private final PlazoMapper plazoMapper;
    private final PlazoParametroMapper plazoParametroMapper;

    public PlazoController(PlazoService plazoService, PlazoMapper plazoMapper, PlazoParametroMapper plazoParametroMapper) {
        this.plazoService = plazoService;
        this.plazoMapper = plazoMapper;
        this.plazoParametroMapper = plazoParametroMapper;
    }

    @GetMapping
    public ResponseEntity<List<PlazoResponse>> findAll() {
        List<Plazo> plazos = plazoService.findAll();
        List<PlazoResponse> responses = plazos.stream()
                .map(plazoMapper::toPlazoResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlazoResponse> findById(@PathVariable Long id) {
        Plazo plazo = plazoService.findById(id);
        PlazoResponse response = plazoMapper.toPlazoResponse(plazo);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<PlazoResponse> create(@Valid @RequestBody PlazoRequest request) {
        PlazoResponse response = plazoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlazoResponse> update(@PathVariable Long id, @Valid @RequestBody PlazoRequest request) {
        if (!plazoService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        PlazoResponse response = plazoService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene los parámetros de plazo para una combinación específica de plazo y tipo de prenda
     * GET /api/plazos/{idPlazo}/parametros/{idTipoPrenda}
     */
    @GetMapping("/{idPlazo}/parametros/{idTipoPrenda}")
    public ResponseEntity<PlazoParametroResponse> getParametrosPlazo(@PathVariable Long idPlazo, @PathVariable Integer idTipoPrenda) {
        PlazoParametro parametro = plazoService.getParametrosPlazo(idPlazo, idTipoPrenda);
        if (parametro == null) {
            return ResponseEntity.notFound().build();
        }
        PlazoParametroResponse response = plazoParametroMapper.toPlazoParametroResponse(parametro);
        return ResponseEntity.ok(response);
    }
}

