package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.CatalogoMapper;
import com.ignis.prestamil.model.Catalogo;
import com.ignis.prestamil.response.CatalogoResponse;
import com.ignis.prestamil.service.CatalogoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final CatalogoService catalogoService;
    private final CatalogoMapper catalogoMapper;

    public CatalogoController(CatalogoService catalogoService, CatalogoMapper catalogoMapper) {
        this.catalogoService = catalogoService;
        this.catalogoMapper = catalogoMapper;
    }

    @GetMapping
    public ResponseEntity<List<CatalogoResponse>> findAll() {
        List<Catalogo> catalogos = catalogoService.findAll();
        List<CatalogoResponse> responses = catalogos.stream()
                .map(catalogoMapper::toCatalogoResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogoResponse> findById(@PathVariable Integer id) {
        Optional<Catalogo> catalogo = catalogoService.findById(id);
        return catalogo.map(c -> ResponseEntity.ok(catalogoMapper.toCatalogoResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatalogoResponse> create(@RequestBody Catalogo catalogo) {
        Catalogo savedCatalogo = catalogoService.save(catalogo);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoMapper.toCatalogoResponse(savedCatalogo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatalogoResponse> update(@PathVariable Integer id, @RequestBody Catalogo catalogo) {
        if (!catalogoService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        catalogo.setId(id);
        Catalogo updatedCatalogo = catalogoService.update(catalogo);
        return ResponseEntity.ok(catalogoMapper.toCatalogoResponse(updatedCatalogo));
    }

}

