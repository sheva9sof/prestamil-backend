package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContratoRepository extends BaseRepository<Contrato, Long>, JpaSpecificationExecutor<Contrato> {

    Optional<Contrato> findByFolio(String folio);

    List<Contrato> findByClienteIdOrderByCreadoEnDesc(Integer clienteId);

    List<Contrato> findByEstatusOrderByFechaVencimientoAsc(EstatusContrato estatus);

    boolean existsByPlazoId(Long plazoId);

    /** Listado de operación (F2): cliente y plazo se muestran en cada fila, se traen en la misma consulta. */
    @Override
    @EntityGraph(attributePaths = {"cliente", "plazo"})
    Page<Contrato> findAll(Specification<Contrato> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"cliente", "plazo"})
    List<Contrato> findAll(Specification<Contrato> spec, Sort sort);
}
