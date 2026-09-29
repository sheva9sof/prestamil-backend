package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.MovimientoContrato;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
     * Último movimiento vigente (no cancelado) de un contrato.
     *
     * @param contratoId identificador del contrato
     * @return el movimiento más reciente, o vacío si no tiene
     */
    Optional<MovimientoContrato> findFirstByContratoIdAndCanceladoFalseOrderByFechaDescIdDesc(Long contratoId);

    /**
     * Lista los movimientos registrados en un turno (para corte de caja).
     *
     * @param turnoId identificador del turno
     * @return lista de movimientos del turno
     */
    List<MovimientoContrato> findByTurnoId(Integer turnoId);

    /**
     * Movimiento registrado con un identificador de operación (idempotencia del cobro).
     *
     * @param requestId identificador generado por el frontend al abrir la ventana de Cobro
     * @return el movimiento, o vacío si ese identificador no se ha usado
     */
    Optional<MovimientoContrato> findByRequestId(String requestId);
}
