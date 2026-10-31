package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.MovimientoCaja;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoCajaRepository extends BaseRepository<MovimientoCaja, Long> {

    /**
     * Flujos de caja de la sucursal en un periodo, en el orden en que se registraron (corte de caja).
     *
     * @param sucursalId identificador de la sucursal
     * @param desde      inicio del periodo (inclusivo)
     * @param hasta      fin del periodo (exclusivo)
     * @return flujos del periodo
     */
    @Query("SELECT m FROM MovimientoCaja m "
            + "WHERE m.sucursalId = :sucursalId AND m.fecha >= :desde AND m.fecha < :hasta "
            + "ORDER BY m.id")
    List<MovimientoCaja> delPeriodo(@Param("sucursalId") Integer sucursalId,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta);

    /**
     * Primer flujo de caja de la sucursal en un periodo (cierre de día, CAJ-06): el día que lo tiene es el
     * primero que debe cerrarse.
     *
     * @param sucursalId identificador de la sucursal
     * @param desde      inicio del periodo (inclusivo)
     * @param hasta      fin del periodo (exclusivo)
     * @return fecha y hora del primer flujo, o {@code null} si no hubo ninguno
     */
    @Query("SELECT MIN(m.fecha) FROM MovimientoCaja m "
            + "WHERE m.sucursalId = :sucursalId AND m.fecha >= :desde AND m.fecha < :hasta")
    LocalDateTime primerFlujo(@Param("sucursalId") Integer sucursalId,
                              @Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta);
}
