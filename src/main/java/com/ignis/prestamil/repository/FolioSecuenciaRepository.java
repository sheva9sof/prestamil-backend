package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.FolioSecuenciaId;
import com.ignis.prestamil.model.SerieFolio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioSecuenciaRepository extends BaseRepository<FolioSecuencia, FolioSecuenciaId> {

    /**
     * Crea el contador en 0 si la serie aún no tiene fila en la sucursal; si ya existe no lo toca.
     * Es SQL nativo porque el upsert deja la fila bloqueada aunque dos transacciones la creen a la
     * vez: con buscar-y-guardar las dos verían "no existe" y la segunda fallaría por llave duplicada.
     *
     * @param sucursalId identificador de la sucursal
     * @param serie      nombre de la serie ({@code SerieFolio.name()})
     */
    @Modifying
    @Query(value = "INSERT INTO folio_secuencia (id_sucursal, serie, ultimo_folio) VALUES (:sucursalId, :serie, 0) "
            + "ON DUPLICATE KEY UPDATE ultimo_folio = ultimo_folio", nativeQuery = true)
    void crearSiNoExiste(@Param("sucursalId") Integer sucursalId, @Param("serie") String serie);

    /**
     * Contador de la serie bloqueado hasta el fin de la transacción, para que dos operaciones
     * simultáneas no tomen el mismo folio.
     *
     * @param sucursalId identificador de la sucursal
     * @param serie      serie del folio
     * @return el contador, o vacío si la serie aún no tiene fila en la sucursal
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FolioSecuencia> findBySucursalIdAndSerie(Integer sucursalId, SerieFolio serie);
}
