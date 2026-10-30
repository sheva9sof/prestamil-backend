package com.ignis.prestamil.service;

import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.repository.FolioSecuenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Folios consecutivos por sucursal y serie (CAJ-01a), con el mismo patrón que la nota y la bolsa: el
 * contador se bloquea hasta el fin de la transacción de quien pide el folio. Si la operación falla, el
 * incremento se revierte con ella y no quedan huecos; dos operaciones simultáneas no toman el mismo
 * número. El contador nunca baja, así que un folio emitido no se vuelve a emitir.
 */
@Service
public class FolioService {

    private final FolioSecuenciaRepository folioSecuenciaRepository;

    public FolioService(FolioSecuenciaRepository folioSecuenciaRepository) {
        this.folioSecuenciaRepository = folioSecuenciaRepository;
    }

    /**
     * Siguiente folio de la serie en la sucursal. Exige una transacción abierta (la de la operación que
     * usa el folio) para que el folio solo se consuma si esa operación se guarda.
     *
     * @param sucursalId identificador de la sucursal
     * @param serie      serie del folio
     * @return el folio asignado; el primero de cada sucursal y serie es 1
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long siguiente(Integer sucursalId, SerieFolio serie) {
        // La primera vez crea la fila en 0; desde aquí la fila queda bloqueada para esta transacción
        folioSecuenciaRepository.crearSiNoExiste(sucursalId, serie.name());
        FolioSecuencia folio = folioSecuenciaRepository
                .findBySucursalIdAndSerie(sucursalId, serie)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el contador de folios " + serie + " de la sucursal " + sucursalId));
        folio.setUltimoFolio(folio.getUltimoFolio() + 1);
        folioSecuenciaRepository.save(folio);
        return folio.getUltimoFolio();
    }
}
