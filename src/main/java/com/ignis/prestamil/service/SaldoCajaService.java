package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Saldo de efectivo de una caja (CAJ-02). Lo usan los traspasos para no dejar la bóveda ni la ventanilla
 * en negativo. Desde CAJ-05 sale del corte de caja del día, la fuente única (RC-09).
 */
@Service
@Transactional(readOnly = true)
public class SaldoCajaService {

    private final CorteCajaService corteCajaService;
    private final TraspasoCajaRepository traspasoCajaRepository;
    private final Clock clock;

    public SaldoCajaService(CorteCajaService corteCajaService,
                            TraspasoCajaRepository traspasoCajaRepository,
                            Clock clock) {
        this.corteCajaService = corteCajaService;
        this.traspasoCajaRepository = traspasoCajaRepository;
        this.clock = clock;
    }

    /**
     * Saldo de efectivo de la caja en este momento, con los traspasos activos y los flujos ya guardados.
     * <ul>
     *   <li>VENTANILLA: la diferencia del arqueo de hoy (RC-07), lo que debe haber en la gaveta. Solo
     *       efectivo (RC-06).</li>
     *   <li>BÓVEDA: el saldo final de hoy (RC-08), que arrastra el saldo desde el arranque.</li>
     *   <li>CONCENTRADORA: el neto de todos sus traspasos. Es el banco o la tesorería: la sucursal no lleva
     *       su saldo real, el corte no la incluye y el valor solo es informativo.</li>
     * </ul>
     *
     * @param caja caja de la sucursal
     * @return saldo de efectivo; puede ser negativo en la concentradora
     * @throws BadRequestException si la bóveda o la ventanilla todavía no tienen corte (sin saldo de
     *                             arranque, o hoy es anterior al arranque)
     */
    public BigDecimal saldoActual(Caja caja) {
        return switch (caja.getTipo()) {
            case VENTANILLA -> corteDeHoy(caja).diferencia();
            case BOVEDA -> corteDeHoy(caja).saldoFinal();
            case CONCENTRADORA -> traspasoCajaRepository.netoTraspasos(caja.getId());
        };
    }

    private CorteCajaResultado corteDeHoy(Caja caja) {
        CorteCajaResultado corte = corteCajaService.calcular(caja.getSucursalId(), LocalDate.now(clock));
        if (!corte.valido()) {
            throw new BadRequestException(corte.aviso());
        }
        return corte;
    }
}
