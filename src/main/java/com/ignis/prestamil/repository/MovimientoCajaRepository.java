package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.MovimientoCaja;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface MovimientoCajaRepository extends BaseRepository<MovimientoCaja, Long> {

    /**
     * Efectivo neto que entró a la gaveta de la sucursal en un periodo: entradas menos salidas, solo la
     * parte en efectivo (RC-06; la tarjeta no entra a la gaveta).
     *
     * @param sucursalId identificador de la sucursal
     * @param desde      inicio del periodo (inclusivo)
     * @param hasta      fin del periodo (exclusivo)
     * @return efectivo neto; 0 si no hubo movimientos
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipo = com.ignis.prestamil.model.TipoMovimientoCaja.ENTRADA "
            + "THEN m.importeEfectivo ELSE -m.importeEfectivo END), 0) "
            + "FROM MovimientoCaja m "
            + "WHERE m.sucursalId = :sucursalId AND m.fecha >= :desde AND m.fecha < :hasta")
    BigDecimal efectivoNeto(@Param("sucursalId") Integer sucursalId,
                            @Param("desde") LocalDateTime desde,
                            @Param("hasta") LocalDateTime hasta);
}
