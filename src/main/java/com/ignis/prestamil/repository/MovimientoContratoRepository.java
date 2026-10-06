package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.TipoMovimiento;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
     * Último movimiento vigente excluyendo un tipo. Soporta RN-26 / C-03: las reposiciones (RE) no se
     * cancelan y tampoco cuentan al decidir cuál es el último movimiento cancelable del contrato.
     *
     * @param contratoId identificador del contrato
     * @param tipo       tipo a excluir (en C-03: {@link TipoMovimiento#RE})
     * @return el movimiento más reciente que no sea del tipo excluido, o vacío si no hay
     */
    Optional<MovimientoContrato> findFirstByContratoIdAndCanceladoFalseAndTipoNotOrderByFechaDescIdDesc(
            Long contratoId, TipoMovimiento tipo);

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

    /**
     * ¿Existe un movimiento no cancelado del contrato dentro del rango de fechas y cuyo tipo esté
     * en el conjunto? Soporta la regla "un movimiento por contrato por día" (RN-29, C-01): el
     * servicio y el resolver pasan {@link TipoMovimiento#CUENTAN_UNO_POR_DIA} y la ventana del día.
     *
     * @param contratoId identificador del contrato
     * @param tipos      tipos que cuentan como movimiento del día
     * @param desde      inicio del rango (inclusive)
     * @param hasta      fin del rango (exclusive)
     * @return true si existe al menos un movimiento no cancelado que cumple
     */
    boolean existsByContratoIdAndCanceladoFalseAndTipoInAndFechaGreaterThanEqualAndFechaLessThan(
            Long contratoId, Collection<TipoMovimiento> tipos, LocalDateTime desde, LocalDateTime hasta);
}
