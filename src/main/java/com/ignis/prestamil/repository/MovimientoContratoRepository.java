package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.MovimientoContrato;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoContratoRepository extends BaseRepository<MovimientoContrato, Long> {

    /**
     * Lista los movimientos de un contrato ordenados cronológicamente.
     *
     * @param contratoId identificador del contrato
     * @return lista de movimientos del más antiguo al más reciente
     */
    List<MovimientoContrato> findByContratoIdOrderByFechaAsc(Long contratoId);

    /**
     * Lista los movimientos registrados en un turno (para corte de caja).
     *
     * @param turnoId identificador del turno
     * @return lista de movimientos del turno
     */
    List<MovimientoContrato> findByTurnoId(Integer turnoId);
}
