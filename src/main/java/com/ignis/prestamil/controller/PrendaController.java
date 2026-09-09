package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.PrendaMapper;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.response.CatSubtipoPrendaResponse;
import com.ignis.prestamil.response.CatValorPrendaResponse;
import com.ignis.prestamil.response.TipoPrendaResponse;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import com.ignis.prestamil.service.CatSubtipoPrendaService;
import com.ignis.prestamil.service.CatValorPrendaService;
import com.ignis.prestamil.service.TipoPrendaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/prendas")
public class PrendaController {

    private final TipoPrendaService tipoPrendaService;
    private final CatSubtipoPrendaService catSubtipoPrendaService;
    private final CatValorPrendaService catValorPrendaService;
    private final PrendaMapper prendaMapper;

    public PrendaController(
            TipoPrendaService tipoPrendaService,
            CatSubtipoPrendaService catSubtipoPrendaService,
            CatValorPrendaService catValorPrendaService,
            PrendaMapper prendaMapper) {
        this.tipoPrendaService = tipoPrendaService;
        this.catSubtipoPrendaService = catSubtipoPrendaService;
        this.catValorPrendaService = catValorPrendaService;
        this.prendaMapper = prendaMapper;
    }

    /**
     * Obtiene todos los tipos de prenda
     * GET /api/prendas/tipos
     */
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoPrendaResponse>> getTiposPrenda() {
        List<TipoPrenda> tiposPrenda = tipoPrendaService.findAllOrdered();
        List<TipoPrendaResponse> responses = tiposPrenda.stream()
                .map(prendaMapper::toTipoPrendaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Obtiene todos los subtipos de prenda por tipo de prenda
     * GET /api/prendas/subtipos/{idTipoPrenda}
     */
    @GetMapping("/subtipos/{idTipoPrenda}")
    public ResponseEntity<List<CatSubtipoPrendaResponse>> getSubtiposPrenda(@PathVariable Integer idTipoPrenda) {
        List<CatSubtipoPrenda> subtiposPrenda = catSubtipoPrendaService.findByIdTipoPrenda(idTipoPrenda);
        List<CatSubtipoPrendaResponse> responses = subtiposPrenda.stream()
                .map(prendaMapper::toCatSubtipoPrendaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Obtiene todos los valores de prenda por subtipo de prenda
     * GET /api/prendas/valores/{idAtributo}
     */
    @GetMapping("/valores/{idAtributo}")
    public ResponseEntity<List<CatValorPrendaResponse>> getValoresPrenda(@PathVariable Integer idAtributo) {
        List<CatValorPrenda> valoresPrenda = catValorPrendaService.findByIdAtributo(idAtributo);
        List<CatValorPrendaResponse> responses = valoresPrenda.stream()
                .map(prendaMapper::toCatValorPrendaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Obtiene todos los valores del catálogo de prendas.
     * GET /api/prendas/valores
     */
    @GetMapping("/valores")
    public ResponseEntity<List<CatValorPrendaResponse>> getAllValoresPrenda() {
        List<CatValorPrendaResponse> responses = catValorPrendaService.findAllOrdered().stream()
                .map(prendaMapper::toCatValorPrendaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Crea un valor seleccionable dentro del catálogo de prendas.
     * POST /api/prendas/valores
     */
    @PostMapping("/valores")
    public ResponseEntity<CatValorPrendaResponse> createValorPrenda(
            @Valid @RequestBody CatValorPrendaRequest request) {
        CatValorPrenda saved = catValorPrendaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prendaMapper.toCatValorPrendaResponse(saved));
    }

    /**
     * Actualiza los datos editables de un valor del catálogo de prendas.
     * PUT /api/prendas/valores/{idValorAtributo}
     */
    @PutMapping("/valores/{idValorAtributo}")
    public ResponseEntity<CatValorPrendaResponse> updateValorPrenda(
            @PathVariable Integer idValorAtributo,
            @Valid @RequestBody CatValorPrendaRequest request) {
        CatValorPrenda updated = catValorPrendaService.update(idValorAtributo, request);
        return ResponseEntity.ok(prendaMapper.toCatValorPrendaResponse(updated));
    }

    /**
     * Elimina físicamente un valor del catálogo de prendas.
     * Rechaza con 400 si el valor ya fue usado en alguna partida de contrato.
     * DELETE /api/prendas/valores/{idValorAtributo}
     */
    @DeleteMapping("/valores/{idValorAtributo}")
    public ResponseEntity<Void> deleteValorPrenda(@PathVariable Integer idValorAtributo) {
        catValorPrendaService.deleteValor(idValorAtributo);
        return ResponseEntity.noContent().build();
    }

}

