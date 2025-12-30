package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.CatalogoMapper;
import com.ignis.prestamil.model.Catalogo;
import com.ignis.prestamil.response.CatalogoResponse;
import com.ignis.prestamil.service.CatalogoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public List<CatalogoResponse> findAll() {
        return catalogoService.findAll().stream()
                .map(catalogoMapper::toCatalogoResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/tipo/{idTipoCatalogo}")
    public ResponseEntity<List<CatalogoResponse>> findByIdTipoCatalogo(@PathVariable Integer idTipoCatalogo) {
        List<Catalogo> catalogos = catalogoService.findByIdTipoCatalogo(idTipoCatalogo);
        List<CatalogoResponse> responses = catalogos.stream()
                .map(catalogoMapper::toCatalogoResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public CatalogoResponse findById(@PathVariable Integer id) {
        Catalogo catalogo = catalogoService.findById(id);
        return catalogoMapper.toCatalogoResponse(catalogo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogoResponse create(@RequestBody Catalogo catalogo) {
        Catalogo savedCatalogo = catalogoService.save(catalogo);
        return catalogoMapper.toCatalogoResponse(savedCatalogo);
    }

    @PutMapping("/{id}")
    public CatalogoResponse update(@PathVariable Integer id, @RequestBody Catalogo catalogoDetails) {
        catalogoDetails.setId(id);
        Catalogo updatedCatalogo = catalogoService.update(catalogoDetails);
        return catalogoMapper.toCatalogoResponse(updatedCatalogo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        catalogoService.deleteById(id);
    }
}
