package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContratoRepository extends BaseRepository<Contrato, Long> {

    Optional<Contrato> findByFolio(String folio);

    List<Contrato> findByClienteIdOrderByCreadoEnDesc(Integer clienteId);

    List<Contrato> findByEstatusOrderByFechaVencimientoAsc(EstatusContrato estatus);

    boolean existsByPlazoId(Long plazoId);
}
