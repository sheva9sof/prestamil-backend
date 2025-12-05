package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Integer> { // El ID es Integer

    /**
     * Busca un turno activo para un usuario específico.
     */
    Optional<Turno> findByUsuarioIdAndActivo(Integer usuarioId, Boolean activo);

    /**
     * Busca si existe un turno activo en el sistema.
     */
    Optional<Turno> findByActivo(Boolean activo);
}