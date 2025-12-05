package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.exception.ValidationException;
import com.ignis.prestamil.mapper.TurnoMapper;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.TurnoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TurnoMapper turnoMapper;

    @Transactional
    public TurnoResponse iniciarTurno() {
        // 1. Obtener el usuario autenticado
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + nombreUsuario));

        // 2. Validar que no exista NINGÚN turno activo en el sistema.
        turnoRepository.findByActivo(true)
                .ifPresent(t -> {
                    throw new ValidationException("Ya existe un turno activo en el sistema con ID: " + t.getId());
                });

        // 3. Crear, configurar y guardar el nuevo turno
        Turno nuevoTurno = new Turno();
        nuevoTurno.setUsuario(usuario);
        nuevoTurno.setFechaInicio(LocalDateTime.now());
        nuevoTurno.setActivo(true);

        Turno turnoGuardado = turnoRepository.save(nuevoTurno);

        return turnoMapper.toTurnoResponse(turnoGuardado);
    }

    @Transactional
    public TurnoResponse cerrarTurno(Integer turnoId) {
        // 1. Buscar el turno que se quiere cerrar
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado con ID: " + turnoId));

        // 2. Validar que el turno esté abierto
        if (!turno.getActivo()) {
            throw new ValidationException("El turno con ID " + turnoId + " ya se encuentra cerrado.");
        }

        // 3. Actualizar datos y guardar
        turno.setFechaFin(LocalDateTime.now());
        turno.setActivo(false);

        Turno turnoCerrado = turnoRepository.save(turno);

        return turnoMapper.toTurnoResponse(turnoCerrado);
    }

    @Transactional(readOnly = true)
    public TurnoResponse obtenerTurnoActivo() {
        // Busca CUALQUIER turno activo en el sistema.
        // Si lo encuentra, lo mapea a TurnoResponse.
        // Si no lo encuentra, devuelve null.
        return turnoRepository.findByActivo(true)
                .map(turnoMapper::toTurnoResponse)
                .orElse(null);
    }
}
