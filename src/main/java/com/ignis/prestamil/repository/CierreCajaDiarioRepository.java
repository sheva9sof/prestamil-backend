package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.CierreCajaDiario;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CierreCajaDiarioRepository extends BaseRepository<CierreCajaDiario, Long> {

    Optional<CierreCajaDiario> findBySucursalIdAndFecha(Integer sucursalId, LocalDate fecha);

    /** Último cierre anterior a un día: de él sale el saldo anterior de los días que siguen. */
    Optional<CierreCajaDiario> findFirstBySucursalIdAndFechaLessThanOrderByFechaDesc(Integer sucursalId,
                                                                                     LocalDate fecha);

    /** Cierres de un periodo (inclusivo), en orden. */
    List<CierreCajaDiario> findBySucursalIdAndFechaBetweenOrderByFechaAsc(Integer sucursalId, LocalDate desde,
                                                                          LocalDate hasta);

    boolean existsBySucursalId(Integer sucursalId);
}
