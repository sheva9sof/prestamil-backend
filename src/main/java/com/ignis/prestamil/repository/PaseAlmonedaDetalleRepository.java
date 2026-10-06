package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.TipoCambioPase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaseAlmonedaDetalleRepository extends BaseRepository<PaseAlmonedaDetalle, Long> {

    /**
     * Detalle del pase de una sucursal en una fecha, filtrado por tipo de cambio. Trae el contrato,
     * su cliente/direccion y la partida con su tipo de prenda para armar las dos pestañas sin N+1.
     *
     * @param sucursalId sucursal
     * @param fecha      fecha del pase
     * @param tipoCambio {@link TipoCambioPase#VENCIDO} (cartera vencida) o {@link TipoCambioPase#EN_VENTA}
     * @return filas de detalle, posiblemente vacio
     */
    @EntityGraph(attributePaths = {"contrato", "contrato.cliente", "contrato.cliente.direccion",
            "partida", "partida.tipoPrenda"})
    List<PaseAlmonedaDetalle> findByPaseSucursalIdAndPaseFechaAndTipoCambio(
            Integer sucursalId, LocalDate fecha, TipoCambioPase tipoCambio);
}
