package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.CajaArranque;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CajaArranqueRepository extends BaseRepository<CajaArranque, Integer> {

    /**
     * Arranque de la sucursal bloqueado hasta el fin de la transacción: lo usan el cierre de día y la
     * captura de Sistemas, que no deben correr a la vez que una operación de caja.
     *
     * @param sucursalId identificador de la sucursal
     * @return el arranque, o vacío si la sucursal no tiene
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CajaArranque a WHERE a.sucursalId = :sucursalId")
    Optional<CajaArranque> findByIdParaActualizar(@Param("sucursalId") Integer sucursalId);

    /**
     * Último día cerrado de la sucursal, leído con bloqueo de la fila: espera a un cierre en curso y ve
     * siempre el valor confirmado más reciente, aunque la transacción ya haya leído antes. Es SQL nativo
     * para no tomar la entidad del contexto de persistencia con un valor viejo.
     *
     * @param sucursalId identificador de la sucursal
     * @return una fila con el último día cerrado (puede ser {@code null}; {@code java.sql.Date} o
     *         {@code LocalDate} según el driver); vacía si no hay arranque
     */
    @Query(value = "SELECT fecha_ultimo_cierre FROM caja_arranque WHERE id_sucursal = :sucursalId FOR UPDATE",
            nativeQuery = true)
    List<Object> ultimoCierreBloqueado(@Param("sucursalId") Integer sucursalId);
}
