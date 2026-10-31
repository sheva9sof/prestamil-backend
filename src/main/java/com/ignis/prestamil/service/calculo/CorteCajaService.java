package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.CierreCajaDiario;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.repository.CierreCajaDiarioRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository.TotalTraspasoDia;
import com.ignis.prestamil.service.ParteCorte;
import com.ignis.prestamil.service.ResolutorRenglonCorte;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor del corte de caja (CAJ-05), fuente única de las cifras de caja (RC-09). Arma los dos bloques del
 * PDF "CORTE DE CAJA" de COCAE, en el orden de la sección 3 de PLAN-CORTE-CAJA:
 * <ul>
 *   <li>Renglones de ventanilla: salen de {@code movimiento_caja} por {@link ResolutorRenglonCorte}, que
 *       divide los RC y resta cada cancelación del renglón de su operación original (Q-C1, Q-C3).
 *       DOTACIONES y RECOLECCIÓN salen de {@code traspaso_caja}.</li>
 *   <li>Bloque inferior: bóveda y saldo final con RC-08 y arqueo de ventanilla con RC-07. Solo el
 *       efectivo entra al arqueo; la tarjeta es informativa (RC-06).</li>
 * </ul>
 *
 * <p>El corte empieza en el día de arranque ({@link SaldoArranqueProvider}). Un día cerrado (CAJ-06) es
 * el corte congelado de su {@code cierre_caja_diario}, no un cálculo nuevo. El saldo anterior sale del
 * saldo final del último cierre; solo los días posteriores a ese cierre se calculan al momento. Como el
 * saldo de la bóveda solo cambia con traspasos (RC-08), para llegar al primer día del periodo basta sumar
 * el neto de los traspasos de la bóveda desde el último cierre (o desde el arranque, si no hay cierres).</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CorteCajaService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ResolutorRenglonCorte resolutorRenglonCorte;
    private final TraspasoCajaRepository traspasoCajaRepository;
    private final SaldoArranqueProvider saldoArranqueProvider;
    private final CierreCajaDiarioRepository cierreCajaDiarioRepository;

    /**
     * Corte de caja de un día.
     *
     * @param sucursalId sucursal del corte
     * @param fecha      día del corte
     * @return el corte con sus dos bloques, o sin cifras y con aviso si el día es anterior al arranque
     *         o no hay saldo de arranque
     */
    public CorteCajaResultado calcular(Integer sucursalId, LocalDate fecha) {
        return calcularRango(sucursalId, fecha, fecha).get(0);
    }

    /**
     * Cortes de caja de cada día de un periodo, para el mensual (REP-06). El saldo final de un día es el
     * saldo anterior del siguiente (RC-08). Los días cerrados salen congelados de su cierre; el resto usa
     * una consulta para los flujos de caja del periodo, una para sus movimientos de contrato y una para
     * los traspasos desde el último cierre anterior al periodo.
     *
     * @param sucursalId sucursal del corte
     * @param desde      primer día (inclusivo)
     * @param hasta      último día (inclusivo)
     * @return un corte por día, en orden; los días anteriores al arranque van sin cifras y con aviso
     * @throws IllegalArgumentException si {@code desde} es posterior a {@code hasta}
     */
    public List<CorteCajaResultado> calcularRango(Integer sucursalId, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("El periodo del corte empieza después de terminar: "
                    + desde + " a " + hasta);
        }
        List<CorteCajaResultado> cortes = new ArrayList<>();

        Optional<SaldoArranque> arranque = saldoArranqueProvider.saldoArranque(sucursalId);
        if (arranque.isEmpty()) {
            String aviso = "La sucursal " + sucursalId + " no tiene saldo de arranque de caja: "
                    + "Sistemas debe capturarlo antes de hacer cortes o traspasos.";
            for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                cortes.add(CorteCajaResultado.sinCifras(sucursalId, dia, aviso));
            }
            return cortes;
        }

        // Antes del arranque el sistema no tiene cifras de caja: aviso, no números
        LocalDate fechaArranque = arranque.get().fecha();
        LocalDate inicio = desde.isBefore(fechaArranque) ? fechaArranque : desde;
        String avisoAnterior = "El corte de caja empieza el " + fechaArranque.format(DD_MM_YYYY)
                + " (saldo de arranque): para días anteriores no hay cifras.";
        for (LocalDate dia = desde; dia.isBefore(inicio) && !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            cortes.add(CorteCajaResultado.sinCifras(sucursalId, dia, avisoAnterior));
        }
        if (inicio.isAfter(hasta)) {
            return cortes;
        }

        // Punto de partida: el saldo final del último cierre antes del periodo; sin cierres, el arranque
        Optional<CierreCajaDiario> cierreAnterior = cierreCajaDiarioRepository
                .findFirstBySucursalIdAndFechaLessThanOrderByFechaDesc(sucursalId, inicio);
        LocalDate partida = cierreAnterior.map(c -> c.getFecha().plusDays(1)).orElse(fechaArranque);
        BigDecimal saldoAnterior = cierreAnterior.map(CierreCajaDiario::getSaldoFinal).orElse(arranque.get().saldo());

        // Los traspasos entre el punto de partida y el periodo llevan el saldo al primer día
        Map<LocalDate, Map<TipoTraspasoCaja, BigDecimal>> traspasos = traspasosPorDia(sucursalId, partida, hasta);
        for (LocalDate dia = partida; dia.isBefore(inicio); dia = dia.plusDays(1)) {
            saldoAnterior = saldoAnterior.add(netoBoveda(traspasos.getOrDefault(dia, Map.of())));
        }

        Map<LocalDate, CierreCajaDiario> cierres = new HashMap<>();
        for (CierreCajaDiario cierre : cierreCajaDiarioRepository
                .findBySucursalIdAndFechaBetweenOrderByFechaAsc(sucursalId, inicio, hasta)) {
            cierres.put(cierre.getFecha(), cierre);
        }
        Map<LocalDate, Map<RenglonCorte, ParteCorte>> flujos = resolutorRenglonCorte.totalesPorDia(sucursalId, inicio, hasta);
        for (LocalDate dia = inicio; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            // Un día cerrado se queda como estaba al cerrar (CAJ-06)
            CierreCajaDiario cierre = cierres.get(dia);
            CorteCajaResultado corte = cierre != null
                    ? CorteCajaJson.leer(cierre.getCorteJson())
                    : dia(sucursalId, dia, saldoAnterior, flujos.get(dia), traspasos.getOrDefault(dia, Map.of()));
            cortes.add(corte);
            saldoAnterior = corte.saldoFinal();
        }
        return cortes;
    }

    /** Corte de un día con su saldo anterior ya calculado. */
    private static CorteCajaResultado dia(Integer sucursalId, LocalDate fecha, BigDecimal saldoAnterior,
                                          Map<RenglonCorte, ParteCorte> flujos,
                                          Map<TipoTraspasoCaja, BigDecimal> traspasos) {
        BigDecimal ministraciones = importe(traspasos, TipoTraspasoCaja.MINISTRACION);
        BigDecimal concentraciones = importe(traspasos, TipoTraspasoCaja.CONCENTRACION);
        BigDecimal dotaciones = importe(traspasos, TipoTraspasoCaja.DOTACION);
        BigDecimal recolecciones = importe(traspasos, TipoTraspasoCaja.RECOLECCION);

        // Bloque superior: los flujos de ventanilla más los dos renglones de traspasos
        Map<RenglonCorte, ParteCorte> partes = new EnumMap<>(flujos);
        partes.put(RenglonCorte.DOTACIONES, new ParteCorte(RenglonCorte.DOTACIONES, dotaciones, CERO));
        partes.put(RenglonCorte.RECOLECCION, new ParteCorte(RenglonCorte.RECOLECCION, recolecciones, CERO));

        List<CorteCajaResultado.Renglon> renglones = new ArrayList<>();
        BigDecimal ingresosEfectivo = CERO;
        BigDecimal ingresosTarjeta = CERO;
        BigDecimal egresosEfectivo = CERO;
        BigDecimal egresosTarjeta = CERO;
        for (RenglonCorte renglon : RenglonCorte.values()) {
            ParteCorte parte = partes.get(renglon);
            renglones.add(new CorteCajaResultado.Renglon(renglon, renglon.getEtiqueta(), renglon.getColumna(),
                    parte.efectivo(), parte.tarjeta()));
            if (renglon.getColumna() == RenglonCorte.Columna.INGRESO) {
                ingresosEfectivo = ingresosEfectivo.add(parte.efectivo());
                ingresosTarjeta = ingresosTarjeta.add(parte.tarjeta());
            } else {
                egresosEfectivo = egresosEfectivo.add(parte.efectivo());
                egresosTarjeta = egresosTarjeta.add(parte.tarjeta());
            }
        }

        // Bloque inferior. Las columnas incluyen los traspasos de ventanilla; el arqueo los separa (RC-07)
        BigDecimal ingresosVentanilla = ingresosEfectivo.subtract(dotaciones);
        BigDecimal egresosVentanilla = egresosEfectivo.subtract(recolecciones);
        BigDecimal diferencia = dotaciones.add(ingresosVentanilla).subtract(egresosVentanilla).subtract(recolecciones);
        BigDecimal boveda = saldoAnterior.add(ministraciones).subtract(concentraciones);
        BigDecimal saldoFinal = boveda.subtract(dotaciones).add(recolecciones);

        return new CorteCajaResultado(sucursalId, fecha, null, renglones,
                new CorteCajaResultado.Totales(ingresosEfectivo, ingresosTarjeta),
                new CorteCajaResultado.Totales(egresosEfectivo, egresosTarjeta),
                saldoAnterior, ministraciones, concentraciones, boveda,
                dotaciones, ingresosVentanilla, egresosVentanilla, recolecciones,
                diferencia, indicador(diferencia), saldoFinal);
    }

    private Map<LocalDate, Map<TipoTraspasoCaja, BigDecimal>> traspasosPorDia(Integer sucursalId, LocalDate desde,
                                                                             LocalDate hasta) {
        Map<LocalDate, Map<TipoTraspasoCaja, BigDecimal>> porDia = new HashMap<>();
        for (TotalTraspasoDia total : traspasoCajaRepository.totalesPorDiaYTipo(sucursalId, desde, hasta)) {
            porDia.computeIfAbsent(total.getFechaOperacion(), d -> new EnumMap<>(TipoTraspasoCaja.class))
                    .put(total.getTipo(), total.getImporte());
        }
        return porDia;
    }

    /** Lo que los traspasos de un día cambian el saldo de la bóveda (RC-08). */
    private static BigDecimal netoBoveda(Map<TipoTraspasoCaja, BigDecimal> traspasos) {
        return importe(traspasos, TipoTraspasoCaja.MINISTRACION)
                .subtract(importe(traspasos, TipoTraspasoCaja.CONCENTRACION))
                .subtract(importe(traspasos, TipoTraspasoCaja.DOTACION))
                .add(importe(traspasos, TipoTraspasoCaja.RECOLECCION));
    }

    private static BigDecimal importe(Map<TipoTraspasoCaja, BigDecimal> traspasos, TipoTraspasoCaja tipo) {
        return traspasos.getOrDefault(tipo, CERO);
    }

    /** RC-07: lo que debería quedar en la gaveta y no se recolectó falta; si es negativo, sobra. */
    private static CorteCajaResultado.Indicador indicador(BigDecimal diferencia) {
        return switch (diferencia.signum()) {
            case 1 -> CorteCajaResultado.Indicador.F;
            case -1 -> CorteCajaResultado.Indicador.S;
            default -> null;
        };
    }
}
