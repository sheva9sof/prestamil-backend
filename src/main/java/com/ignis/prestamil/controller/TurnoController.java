package com.ignis.prestamil.controller;

import com.ignis.prestamil.response.TurnoResponse;
import com.ignis.prestamil.service.TurnoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
@Slf4j
public class TurnoController {

    private final TurnoService turnoService;
    private final LogoutStreamController logoutStreamController;

    @PostMapping("/iniciar")
    public ResponseEntity<TurnoResponse> iniciarTurno() {
        log.info("Petición recibida: Iniciar Turno");
        TurnoResponse turnoResponse = turnoService.iniciarTurno();
        return new ResponseEntity<>(turnoResponse, HttpStatus.CREATED);
    }

    @PostMapping("/cerrar/{id}")
    public ResponseEntity<TurnoResponse> cerrarTurno(@PathVariable("id") Integer turnoId) {
        log.info("Petición recibida: Cerrar Turno ID {}", turnoId);
        TurnoResponse turnoResponse = turnoService.cerrarTurno(turnoId);

        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        String message = "El turno ha sido cerrado por " + currentUser;

        List<String> connectedUsers = logoutStreamController.getConnectedUsers();
        for (String user : connectedUsers) {
            logoutStreamController.sendTurnoCerradoEvent(user, message);
        }

        return ResponseEntity.ok(turnoResponse);
    }

    @GetMapping("/activo")
    public ResponseEntity<TurnoResponse> obtenerTurnoActivo() {
        log.info("Petición recibida: Obtener Turno Activo");
        TurnoResponse turnoResponse = turnoService.obtenerTurnoActivo();
        return ResponseEntity.ok(turnoResponse);
    }
}
