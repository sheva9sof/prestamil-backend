package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PaseAlmoneda;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PaseAlmonedaRepository extends BaseRepository<PaseAlmoneda, Long> {

    /**
     * ¿Ya se ejecuto el pase de almoneda hoy en esta sucursal? Soporta la idempotencia de F11: el
     * primer turno del dia lo dispara y los subsecuentes lo ven ya marcado.
     *
     * @param sucursalId sucursal evaluada
     * @param fecha      fecha del servidor
     * @return true si existe el registro
     */
    boolean existsBySucursalIdAndFecha(Integer sucursalId, LocalDate fecha);

    /**
     * Ultimo pase corrido en la sucursal; de ahi arranca la puesta al dia de
     * {@code PaseAlmonedaService.ejecutarPendientes}.
     *
     * @param sucursalId sucursal evaluada
     * @return el pase con la fecha mas reciente, si existe
     */
    Optional<PaseAlmoneda> findFirstBySucursalIdOrderByFechaDesc(Integer sucursalId);
}
