package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Saldo de efectivo de una caja (CAJ-02). Lo usan los traspasos para no dejar la bóveda ni la ventanilla
 * en negativo.
 *
 * <p>TODO CAJ-05: implementación provisional, sin saldo anterior ni saldo de arranque. Se reemplaza por
 * {@code CorteCajaService}, que es la fuente única del saldo (RC-09).</p>
 */
@Service
@Transactional(readOnly = true)
public class SaldoCajaService {

    private final TraspasoCajaRepository traspasoCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final Clock clock;

    public SaldoCajaService(TraspasoCajaRepository traspasoCajaRepository,
                            MovimientoCajaRepository movimientoCajaRepository,
                            Clock clock) {
        this.traspasoCajaRepository = traspasoCajaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.clock = clock;
    }

    /**
     * Saldo de efectivo de la caja en este momento, con los traspasos activos y los flujos ya guardados.
     * <ul>
     *   <li>VENTANILLA: traspasos del día más el efectivo neto de {@code movimiento_caja} del día (RC-07).
     *       La gaveta se arquea por día; la tarjeta no cuenta (RC-06).</li>
     *   <li>BÓVEDA: todos sus traspasos, porque el saldo se arrastra de un día al siguiente (RC-08).
     *       Todavía sin saldo de arranque (CAJ-06).</li>
     *   <li>CONCENTRADORA: todos sus traspasos. Es el banco o la tesorería: la sucursal no lleva su
     *       saldo real y el valor solo es informativo.</li>
     * </ul>
     *
     * @param caja caja de la sucursal
     * @return saldo de efectivo; puede ser negativo en la concentradora
     */
    public BigDecimal saldoActual(Caja caja) {
        return switch (caja.getTipo()) {
            case VENTANILLA -> {
                LocalDate hoy = LocalDate.now(clock);
                BigDecimal traspasos = traspasoCajaRepository.netoTraspasosDelDia(caja.getId(), hoy);
                BigDecimal flujos = movimientoCajaRepository.efectivoNeto(
                        caja.getSucursalId(), hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
                yield traspasos.add(flujos);
            }
            case BOVEDA, CONCENTRADORA -> traspasoCajaRepository.netoTraspasos(caja.getId());
        };
    }
}
