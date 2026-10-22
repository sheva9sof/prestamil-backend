package com.ignis.prestamil.service;

import com.ignis.prestamil.model.FolioBolsa;
import com.ignis.prestamil.model.FolioCodigoBarras;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.repository.FolioBolsaRepository;
import com.ignis.prestamil.repository.FolioCodigoBarrasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consecutivos de la bolsa del contrato y del código de barras de cada partida (D-10). Los contadores
 * se bloquean hasta el fin de la transacción que crea el contrato: si el contrato falla no quedan
 * huecos, y dos avalúos simultáneos no toman el mismo número. Los contadores nunca bajan, así que un
 * número asignado no se vuelve a emitir.
 */
@Service
@Transactional
public class BolsaService {

    /** Dígitos del código de barras: numérico con ceros a la izquierda, legible por cualquier lector. */
    static final int DIGITOS_CODIGO_BARRAS = 10;

    private final FolioBolsaRepository folioBolsaRepository;
    private final FolioCodigoBarrasRepository folioCodigoBarrasRepository;

    public BolsaService(FolioBolsaRepository folioBolsaRepository,
                        FolioCodigoBarrasRepository folioCodigoBarrasRepository) {
        this.folioBolsaRepository = folioBolsaRepository;
        this.folioCodigoBarrasRepository = folioCodigoBarrasRepository;
    }

    /**
     * Siguiente número de bolsa de la sucursal.
     *
     * @param sucursalId identificador de la sucursal
     * @return el número de bolsa asignado
     */
    public Integer siguienteNumBolsa(Integer sucursalId) {
        FolioBolsa folio = folioBolsaRepository.findBySucursalId(sucursalId).orElseGet(() -> {
            FolioBolsa nuevo = new FolioBolsa();
            nuevo.setSucursalId(sucursalId);
            return nuevo;
        });
        folio.setUltimoFolio(folio.getUltimoFolio() + 1);
        folioBolsaRepository.save(folio);
        return folio.getUltimoFolio();
    }

    /**
     * Asigna un código de barras nuevo a cada partida, en orden. Reserva todo el bloque con un solo
     * incremento del contador global.
     *
     * @param partidas partidas del contrato que se está creando
     */
    public void asignarCodigosBarras(List<PartidaContrato> partidas) {
        if (partidas.isEmpty()) {
            return;
        }
        FolioCodigoBarras folio = folioCodigoBarrasRepository.findWithLockById(FolioCodigoBarras.ID_UNICO)
                .orElseGet(() -> {
                    FolioCodigoBarras nuevo = new FolioCodigoBarras();
                    nuevo.setId(FolioCodigoBarras.ID_UNICO);
                    return nuevo;
                });
        long siguiente = folio.getUltimoFolio();
        for (PartidaContrato partida : partidas) {
            siguiente++;
            partida.setCodigoBarras(formatoCodigoBarras(siguiente));
        }
        folio.setUltimoFolio(siguiente);
        folioCodigoBarrasRepository.save(folio);
    }

    static String formatoCodigoBarras(long consecutivo) {
        return String.format("%0" + DIGITOS_CODIGO_BARRAS + "d", consecutivo);
    }
}
