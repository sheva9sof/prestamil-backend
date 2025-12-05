package com.ignis.prestamil.controller;

import com.ignis.prestamil.response.TurnoResponse;
import com.ignis.prestamil.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;

    @PostMapping("/iniciar")
    public ResponseEntity<TurnoResponse> iniciarTurno() {
        TurnoResponse turnoResponse = turnoService.iniciarTurno();
        return new ResponseEntity<>(turnoResponse, HttpStatus.CREATED);
    }

    @PostMapping("/cerrar/{id}")
    public ResponseEntity<TurnoResponse> cerrarTurno(@PathVariable("id") Integer turnoId) {
        TurnoResponse turnoResponse = turnoService.cerrarTurno(turnoId);
        return ResponseEntity.ok(turnoResponse);
    }

    @GetMapping("/activo")
    public ResponseEntity<TurnoResponse> obtenerTurnoActivo() {
        TurnoResponse turnoResponse = turnoService.obtenerTurnoActivo();
        return ResponseEntity.ok(turnoResponse);
    }
}