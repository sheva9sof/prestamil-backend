package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TraspasoCajaRepository extends BaseRepository<TraspasoCaja, Long> {

    /** Traspasos del día de la sucursal, de todos los tipos y estatus. */
    List<TraspasoCaja> findBySucursalIdAndFechaOperacionOrderByFolioAsc(Integer sucursalId, LocalDate fechaOperacion);

    /** Traspasos del día de la sucursal de un tipo, de todos los estatus. */
    List<TraspasoCaja> findBySucursalIdAndFechaOperacionAndTipoOrderByFolioAsc(
            Integer sucursalId, LocalDate fechaOperacion, TipoTraspasoCaja tipo);

    /**
     * Traspaso bloqueado hasta el fin de la transacción, para que dos cancelaciones simultáneas no lo
     * cancelen dos veces.
     *
     * @param id identificador del traspaso
     * @return el traspaso, o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TraspasoCaja t WHERE t.id = :id")
    Optional<TraspasoCaja> findByIdParaActualizar(@Param("id") Long id);

    /**
     * Neto de los traspasos activos de una caja en toda su historia: lo que llegó menos lo que salió.
     *
     * @param cajaId identificador de la caja
     * @return neto de traspasos; 0 si la caja no tiene ninguno
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.cajaDestino.id = :cajaId THEN t.importe ELSE -t.importe END), 0) "
            + "FROM TraspasoCaja t "
            + "WHERE t.estatus = com.ignis.prestamil.model.EstatusTraspasoCaja.ACTIVO "
            + "AND (t.cajaDestino.id = :cajaId OR t.cajaOrigen.id = :cajaId)")
    BigDecimal netoTraspasos(@Param("cajaId") Integer cajaId);

    /**
     * Neto de los traspasos activos de una caja en un día: lo que llegó menos lo que salió.
     *
     * @param cajaId         identificador de la caja
     * @param fechaOperacion día del corte
     * @return neto de traspasos del día; 0 si no hubo
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.cajaDestino.id = :cajaId THEN t.importe ELSE -t.importe END), 0) "
            + "FROM TraspasoCaja t "
            + "WHERE t.estatus = com.ignis.prestamil.model.EstatusTraspasoCaja.ACTIVO "
            + "AND (t.cajaDestino.id = :cajaId OR t.cajaOrigen.id = :cajaId) "
            + "AND t.fechaOperacion = :fechaOperacion")
    BigDecimal netoTraspasosDelDia(@Param("cajaId") Integer cajaId,
                                   @Param("fechaOperacion") LocalDate fechaOperacion);
}
