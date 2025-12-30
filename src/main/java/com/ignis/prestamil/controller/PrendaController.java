package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.PrendaMapper;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.response.CatSubtipoPrendaResponse;
import com.ignis.prestamil.response.CatValorPrendaResponse;
import com.ignis.prestamil.response.TipoPrendaResponse;
import com.ignis.prestamil.service.CatSubtipoPrendaService;
import com.ignis.prestamil.service.CatValorPrendaService;
import com.ignis.prestamil.service.TipoPrendaService;
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

}

