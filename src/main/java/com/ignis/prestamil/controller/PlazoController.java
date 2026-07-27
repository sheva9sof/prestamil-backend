package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.mapper.PlazoParametroMapper;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.request.PlazoHechuraAlhajaRequest;
import com.ignis.prestamil.request.PlazoParametroRequest;
import com.ignis.prestamil.request.PlazoRequest;
import com.ignis.prestamil.response.PlazoHechuraAlhajaResponse;
import com.ignis.prestamil.response.PlazoParametroResponse;
import com.ignis.prestamil.response.PlazoResponse;
import com.ignis.prestamil.service.PlazoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        plazoService.eliminarPlazo(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtiene los parámetros de plazo para una combinación específica de plazo, tipo de prenda y sucursal.
     * GET /api/plazos/{idPlazo}/parametros/{idTipoPrenda}?sucursalId=1
     */
    @GetMapping("/{idPlazo}/parametros/{idTipoPrenda}")
    public ResponseEntity<PlazoParametroResponse> getParametrosPlazo(
            @PathVariable Long idPlazo,
            @PathVariable Integer idTipoPrenda,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getParametrosPlazo(idPlazo, idTipoPrenda, sucursalId));
    }

    /**
     * Lista los parámetros de un plazo filtrados por sucursal.
     * GET /api/plazos/{id}/parametros?sucursalId=1
     */
    @GetMapping("/{id}/parametros")
    public ResponseEntity<List<PlazoParametroResponse>> getParametrosBySucursal(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getParametrosBySucursal(id, sucursalId));
    }

    /**
     * Crea o actualiza (upsert) los parámetros para una combinación plazo+tipoPrenda+sucursal.
     * PUT /api/plazos/{id}/parametros/{tipoPrendaId}?sucursalId=1
     */
    @PutMapping("/{id}/parametros/{tipoPrendaId}")
    public ResponseEntity<PlazoParametroResponse> guardarParametro(
            @PathVariable Long id,
            @PathVariable Integer tipoPrendaId,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody PlazoParametroRequest request) {
        return ResponseEntity.ok(plazoService.guardarParametro(id, tipoPrendaId, sucursalId, request));
    }

    /**
     * Obtiene la tabla de hechuras de alhajas para un plazo y sucursal.
     * GET /api/plazos/{id}/alhajas?sucursalId=1
     */
    @GetMapping("/{id}/alhajas")
    public ResponseEntity<List<PlazoHechuraAlhajaResponse>> getTablaAlhajas(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getTablaAlhajas(id, sucursalId));
    }

    /**
     * Crea una nueva combinación de alhaja para un plazo/sucursal.
     * POST /api/plazos/{id}/alhajas?sucursalId=1
     */
    @PostMapping("/{id}/alhajas")
    public ResponseEntity<PlazoHechuraAlhajaResponse> crearAlhaja(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody PlazoHechuraAlhajaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(plazoService.crearAlhaja(id, sucursalId, request));
    }

    /**
     * Actualiza el precio base de un kilataje+hechura específico y recalcula precioPrestamo.
     * PUT /api/plazos/{id}/alhajas/{kilataje}/{hechura}?sucursalId=1
     */
    @PutMapping("/{id}/alhajas/{kilataje}/{hechura}")
    public ResponseEntity<PlazoHechuraAlhajaResponse> actualizarPrecioBase(
            @PathVariable Integer id,
            @PathVariable Integer kilataje,
            @PathVariable String hechura,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal precioBase = body.get("precioBase");
        if (precioBase == null) {
            throw new BadRequestException("precioBase es requerido en el body");
        }
        return ResponseEntity.ok(
                plazoService.actualizarPrecioBase(id, sucursalId, kilataje, hechura, precioBase));
    }

    @PutMapping("/{id}/alhajas/{kilataje}/{hechura}/porcentaje-aumento")
    public ResponseEntity<PlazoHechuraAlhajaResponse> actualizarPorcAumento(
            @PathVariable Integer id,
            @PathVariable Integer kilataje,
            @PathVariable String hechura,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(plazoService.actualizarPorcAumento(
                id, sucursalId, kilataje, hechura, body.get("porcAumento")));
    }

    /**
     * Recalcula todos los precios de hechuras usando el precio base del oro de 24K por onza troy.
     * PUT /api/plazos/{id}/alhajas/precio-oro?sucursalId=1
     */
    @PutMapping("/{id}/alhajas/precio-oro")
    public ResponseEntity<List<PlazoHechuraAlhajaResponse>> actualizarTodosPrecios(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal precioBaseOro = body.get("precioBaseOro");
        if (precioBaseOro == null) {
            throw new BadRequestException("precioBaseOro es requerido en el body");
        }
        plazoService.actualizarTodosPrecios(id, sucursalId, precioBaseOro);
        return ResponseEntity.ok(plazoService.getTablaAlhajas(id, sucursalId));
    }
}
