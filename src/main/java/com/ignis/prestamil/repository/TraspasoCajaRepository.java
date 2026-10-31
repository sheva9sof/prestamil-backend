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

    /** Importe de los traspasos activos de un día y un tipo (corte de caja, CAJ-05). */
    interface TotalTraspasoDia {
        LocalDate getFechaOperacion();

        TipoTraspasoCaja getTipo();

        BigDecimal getImporte();
    }

    /**
     * Traspasos activos de la sucursal sumados por día y tipo, en una sola consulta. Los cancelados no
     * cuentan: solo se cancelan el mismo día (CAJ-02), así que desaparecen de ese corte.
     *
     * @param sucursalId identificador de la sucursal
     * @param desde      primer día (inclusivo)
     * @param hasta      último día (inclusivo)
     * @return un renglón por día y tipo con traspasos; los que no tuvieron no aparecen
     */
    @Query("SELECT t.fechaOperacion AS fechaOperacion, t.tipo AS tipo, SUM(t.importe) AS importe "
            + "FROM TraspasoCaja t "
            + "WHERE t.sucursalId = :sucursalId "
            + "AND t.estatus = com.ignis.prestamil.model.EstatusTraspasoCaja.ACTIVO "
            + "AND t.fechaOperacion >= :desde AND t.fechaOperacion <= :hasta "
            + "GROUP BY t.fechaOperacion, t.tipo")
    List<TotalTraspasoDia> totalesPorDiaYTipo(@Param("sucursalId") Integer sucursalId,
                                              @Param("desde") LocalDate desde,
                                              @Param("hasta") LocalDate hasta);

    /**
     * Primer día con traspasos de la sucursal en un periodo, de cualquier estatus (cierre de día, CAJ-06):
     * un traspaso cancelado también fue una operación de ese día.
     *
     * @param sucursalId identificador de la sucursal
     * @param desde      primer día (inclusivo)
     * @param hasta      último día (exclusivo)
     * @return el primer día con traspasos, o {@code null} si no hubo ninguno
     */
    @Query("SELECT MIN(t.fechaOperacion) FROM TraspasoCaja t "
            + "WHERE t.sucursalId = :sucursalId AND t.fechaOperacion >= :desde AND t.fechaOperacion < :hasta")
    LocalDate primerDiaConTraspasos(@Param("sucursalId") Integer sucursalId,
                                    @Param("desde") LocalDate desde,
                                    @Param("hasta") LocalDate hasta);
}
