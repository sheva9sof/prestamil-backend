package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContratoRepository extends BaseRepository<Contrato, Long>, JpaSpecificationExecutor<Contrato> {

    Optional<Contrato> findByFolio(String folio);

    List<Contrato> findByClienteIdOrderByCreadoEnDesc(Integer clienteId);

    List<Contrato> findByEstatusOrderByFechaVencimientoAsc(EstatusContrato estatus);

    boolean existsByPlazoId(Long plazoId);

    /**
     * Contrato bloqueado hasta el fin de la transacción (SELECT ... FOR UPDATE). Serializa los cobros
     * sobre un mismo contrato: doble clic o dos cajas no pueden aplicar dos movimientos sobre el mismo
     * estado.
     *
     * @param id identificador del contrato
     * @return el contrato bloqueado, o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contrato> findWithLockById(Long id);

    /** Listado de operación (F2): cliente y plazo se muestran en cada fila, se traen en la misma consulta. */
    @Override
    @EntityGraph(attributePaths = {"cliente", "plazo"})
    Page<Contrato> findAll(Specification<Contrato> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "plazo"})
    List<Contrato> findAll(Specification<Contrato> spec, Sort sort);
}
