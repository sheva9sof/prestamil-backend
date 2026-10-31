package com.ignis.prestamil.controller;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.request.ArranqueCajaRequest;
import com.ignis.prestamil.request.CierreCajaRequest;
import com.ignis.prestamil.response.ArranqueCajaResponse;
import com.ignis.prestamil.response.CierreCajaResponse;
import com.ignis.prestamil.service.CajaArranqueService;
import com.ignis.prestamil.service.CierreCajaService;
import com.ignis.prestamil.service.SucursalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cierre de día de caja y saldo de arranque (CAJ-06, B-04). El gerente cierra el día en curso; un día
 * pendiente y el arranque son de Sistemas.
 */
@RestController
@RequestMapping("/api/caja")
@RequiredArgsConstructor
public class CierreCajaController {

    private final CierreCajaService cierreCajaService;
    private final CajaArranqueService cajaArranqueService;
    private final SucursalService sucursalService;

    /**
     * Cierra un día de caja (sin fecha, hoy) y congela su corte.
     * POST /api/caja/cierre
     */
    @PostMapping("/cierre")
    public ResponseEntity<CierreCajaResponse> cerrar(@RequestBody(required = false) CierreCajaRequest request,
                                                     Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cierreCajaService.cerrar(sucursalId(),
                request != null ? request.getFecha() : null, authentication.getName()));
    }

    /**
     * Saldo de arranque de la sucursal y último folio de cada serie.
     * GET /api/caja/arranque
     */
    @GetMapping("/arranque")
    public ResponseEntity<ArranqueCajaResponse> arranque(Authentication authentication) {
        return ResponseEntity.ok(cajaArranqueService.consultar(sucursalId(), authentication.getName()));
    }

    /**
     * Captura el saldo de arranque y los folios iniciales; solo Sistemas y una sola vez.
     * PUT /api/caja/arranque
     */
    @PutMapping("/arranque")
    public ResponseEntity<ArranqueCajaResponse> capturarArranque(@Valid @RequestBody ArranqueCajaRequest request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(cajaArranqueService.capturar(sucursalId(), request, authentication.getName()));
    }

    private Integer sucursalId() {
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no configurada"));
    }
}
