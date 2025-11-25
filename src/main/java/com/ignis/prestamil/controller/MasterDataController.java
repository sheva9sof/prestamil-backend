package com.ignis.prestamil.controller;

import com.ignis.prestamil.service.MasterDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador genérico para proporcionar datos maestros/iniciales
 * que se necesitan al cargar diferentes pantallas
 */
@RestController
@RequestMapping("/api/master-data")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    /**
     * Obtiene los estados de México
     * GET /api/master-data/estados
     */
    @GetMapping("/estados")
    public ResponseEntity<List<String>> getEstados() {
        return ResponseEntity.ok(masterDataService.getEstados());
    }
}

