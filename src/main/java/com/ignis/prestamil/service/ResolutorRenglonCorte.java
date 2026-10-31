package com.ignis.prestamil.service;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lleva cada flujo de {@code movimiento_caja} a su renglón del corte (CAJ-01d, sección 3 de
 * PLAN-CORTE-CAJA, respuestas de Jorge del 2026-10-30). Base del motor del corte (CAJ-05).
 *
 * <ul>
 *   <li><b>Concepto de renglón fijo</b>: todo el importe a {@link ConceptoCaja#renglonCorte()}.</li>
 *   <li><b>RC</b> ({@link ConceptoCaja#ABONO_CAPITAL}): se divide con {@link #dividirAbonoCapital}.</li>
 *   <li><b>Cancelaciones</b> (Q-C1): restan del renglón de la operación original, que sale de
 *       {@code origen_tipo}/{@code origen_id}, el día en que se cancela. Si la operación es del mismo
 *       día, el renglón queda en neto 0.</li>
 * </ul>
 *
 * <p>El RC se divide aquí y no al registrar: así un cobro sigue siendo una sola fila de caja (una nota,
 * un pago), y su cancelación, que se guarda con el mismo reparto efectivo/tarjeta y apunta al mismo
 * movimiento de contrato, pasa por la misma división y lo deja en 0 exacto.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ResolutorRenglonCorte {

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final MovimientoContratoRepository movimientoContratoRepository;

    /**
     * Totales por renglón de los flujos de caja de un día. Solo cubre {@code movimiento_caja}:
     * DOTACIONES y RECOLECCIÓN salen de {@code traspaso_caja} y aquí quedan en 0.
     *
     * @param sucursalId sucursal del corte
     * @param fecha      día del corte; una cancelación cuenta el día en que se cancela
     * @return todos los renglones del corte, en orden, con su efectivo y tarjeta (0 si no hubo flujos)
     */
    public Map<RenglonCorte, ParteCorte> totalesDelDia(Integer sucursalId, LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        return totalesPorRenglon(movimientoCajaRepository.delPeriodo(sucursalId, desde, desde.plusDays(1)));
    }

    /**
     * Suma por renglón las partes de una lista de flujos de caja.
     *
     * @param movimientos flujos de caja
     * @return todos los renglones del corte, en orden, con su efectivo y tarjeta (0 si no hubo flujos)
     */
    public Map<RenglonCorte, ParteCorte> totalesPorRenglon(List<MovimientoCaja> movimientos) {
        Map<RenglonCorte, ParteCorte> totales = ceros();
        partes(movimientos).forEach(parte -> totales.merge(parte.renglon(), parte, ParteCorte::mas));
        return totales;
    }

    /**
     * Totales por renglón de cada día de un periodo, con una consulta para los flujos y otra para sus
     * movimientos de contrato (corte mensual, CAJ-05). Cada flujo cuenta el día de su propia fecha, así
     * que una cancelación resta el día en que se cancela (Q-C1).
     *
     * @param sucursalId sucursal del corte
     * @param desde      primer día (inclusivo)
     * @param hasta      último día (inclusivo)
     * @return un mapa por día del periodo, en orden, con todos los renglones (0 si no hubo flujos)
     */
    public Map<LocalDate, Map<RenglonCorte, ParteCorte>> totalesPorDia(Integer sucursalId, LocalDate desde,
                                                                      LocalDate hasta) {
        List<MovimientoCaja> movimientos = movimientoCajaRepository.delPeriodo(
                sucursalId, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        Map<Long, MovimientoContrato> origenes = origenes(movimientos);

        Map<LocalDate, Map<RenglonCorte, ParteCorte>> porDia = new TreeMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, ceros());
        }
        for (MovimientoCaja m : movimientos) {
            Map<RenglonCorte, ParteCorte> totales = porDia.get(m.getFecha().toLocalDate());
            partes(m, origen(m, origenes)).forEach(parte -> totales.merge(parte.renglon(), parte, ParteCorte::mas));
        }
        return porDia;
    }

    /**
     * Partes del corte de una lista de flujos de caja, en el mismo orden.
     *
     * @param movimientos flujos de caja
     * @return una parte por flujo, dos por cada RC (o su cancelación)
     * @throws IllegalStateException si un RC o una cancelación no tiene el origen que hace falta
     */
    public List<ParteCorte> partes(List<MovimientoCaja> movimientos) {
        Map<Long, MovimientoContrato> origenes = origenes(movimientos);
        List<ParteCorte> partes = new ArrayList<>();
        for (MovimientoCaja m : movimientos) {
            partes.addAll(partes(m, origen(m, origenes)));
        }
        return partes;
    }

    /** Un solo SELECT para los movimientos de contrato que hacen falta: los de los RC y las cancelaciones. */
    private Map<Long, MovimientoContrato> origenes(List<MovimientoCaja> movimientos) {
        Set<Long> ids = movimientos.stream()
                .filter(ResolutorRenglonCorte::requiereMovimientoContrato)
                .map(MovimientoCaja::getOrigenId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : movimientoContratoRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(MovimientoContrato::getId, Function.identity()));
    }

    private static MovimientoContrato origen(MovimientoCaja m, Map<Long, MovimientoContrato> origenes) {
        return m.getOrigenId() != null ? origenes.get(m.getOrigenId()) : null;
    }

    private static Map<RenglonCorte, ParteCorte> ceros() {
        Map<RenglonCorte, ParteCorte> totales = new EnumMap<>(RenglonCorte.class);
        for (RenglonCorte renglon : RenglonCorte.values()) {
            totales.put(renglon, ParteCorte.cero(renglon));
        }
        return totales;
    }

    /**
     * Partes del corte de un flujo de caja.
     *
     * @param m      flujo de caja
     * @param origen movimiento de contrato de {@code origen_id}; obligatorio en RC y en la cancelación de
     *               un movimiento de contrato, ignorado en los demás
     * @return una parte, o dos si es un RC o su cancelación; con signo negativo si es una cancelación
     * @throws IllegalStateException si falta el origen que hace falta
     */
    static List<ParteCorte> partes(MovimientoCaja m, MovimientoContrato origen) {
        ConceptoCaja concepto = m.getConceptoCaja();
        if (!concepto.esCancelacion()) {
            return partesDeOperacion(m, concepto, origen);
        }

        // Q-C1: la cancelación resta, con el mismo reparto efectivo/tarjeta, del renglón de la operación original
        if (m.getOrigenTipo() == null) {
            throw new IllegalStateException("La cancelación de caja " + m.getId() + " (" + concepto
                    + ") no tiene origen: no se sabe de qué renglón restarla");
        }
        List<ParteCorte> original = switch (m.getOrigenTipo()) {
            case MOVIMIENTO_CONTRATO -> partesDeOperacion(m,
                    ConceptoCaja.deMovimiento(requerido(m, origen).getTipo()), origen);
            // Vitrina y fundición van las dos a VENTAS: no hace falta leer la venta, que es de Ventas (A-02)
            case VENTA, CANCELACION_VENTA -> List.of(
                    new ParteCorte(RenglonCorte.VENTAS, m.getImporteEfectivo(), m.getImporteTarjeta()));
        };
        return original.stream().map(ParteCorte::negada).toList();
    }

    /**
     * Regla 2 de la sección 3 (Q-C3): divide un RC en la parte de capital ({@code abono_capital}), que va
     * a ABONOS A CAPITAL, y la de refrendo (interés, sanción e IVA), que va a REFRENDOS o, si el RC cobró
     * sanción, a REFRENDOS EXTEMPORÁNEOS. El efectivo y la tarjeta se reparten en proporción al importe de
     * cada parte; el redondeo va a la parte de capital, así que las sumas siempre dan exacto.
     *
     * @param efectivo     efectivo neto del flujo; {@code >= 0}
     * @param tarjeta      parte con tarjeta; {@code >= 0}
     * @param abonoCapital capital abonado; entre 0 y efectivo + tarjeta
     * @param conSancion   si el RC cobró sanción
     * @return dos partes: primero la de capital y luego la de refrendo
     * @throws IllegalArgumentException si el abono a capital es negativo o mayor que el total
     */
    public static List<ParteCorte> dividirAbonoCapital(BigDecimal efectivo, BigDecimal tarjeta,
                                                       BigDecimal abonoCapital, boolean conSancion) {
        BigDecimal total = efectivo.add(tarjeta);
        if (abonoCapital.signum() < 0 || abonoCapital.compareTo(total) > 0) {
            throw new IllegalArgumentException("Abono a capital fuera de rango: " + abonoCapital
                    + " de un total de " + total);
        }
        BigDecimal refrendo = total.subtract(abonoCapital);

        // La parte de refrendo se redondea; la de capital se queda con lo que falte
        BigDecimal efectivoRefrendo = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : efectivo.multiply(refrendo).divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal tarjetaRefrendo = refrendo.subtract(efectivoRefrendo);

        RenglonCorte renglonRefrendo = conSancion
                ? RenglonCorte.REFRENDOS_EXTEMPORANEOS
                : RenglonCorte.REFRENDOS;
        return List.of(
                new ParteCorte(RenglonCorte.ABONOS_CAPITAL,
                        efectivo.subtract(efectivoRefrendo), tarjeta.subtract(tarjetaRefrendo)),
                new ParteCorte(renglonRefrendo, efectivoRefrendo, tarjetaRefrendo));
    }

    /** Partes de una operación (no cancelación) con los importes del flujo. */
    private static List<ParteCorte> partesDeOperacion(MovimientoCaja m, ConceptoCaja concepto,
                                                      MovimientoContrato origen) {
        if (concepto == ConceptoCaja.ABONO_CAPITAL) {
            MovimientoContrato rc = requerido(m, origen);
            BigDecimal sancion = rc.getSancion() != null ? rc.getSancion() : BigDecimal.ZERO;
            BigDecimal abono = rc.getAbonoCapital() != null ? rc.getAbonoCapital() : BigDecimal.ZERO;
            return dividirAbonoCapital(m.getImporteEfectivo(), m.getImporteTarjeta(), abono, sancion.signum() > 0);
        }
        return List.of(new ParteCorte(concepto.renglonCorte(), m.getImporteEfectivo(), m.getImporteTarjeta()));
    }

    private static boolean requiereMovimientoContrato(MovimientoCaja m) {
        return m.getOrigenTipo() == OrigenCaja.MOVIMIENTO_CONTRATO
                && (m.getConceptoCaja() == ConceptoCaja.ABONO_CAPITAL || m.getConceptoCaja().esCancelacion());
    }

    private static MovimientoContrato requerido(MovimientoCaja m, MovimientoContrato origen) {
        if (m.getOrigenTipo() != OrigenCaja.MOVIMIENTO_CONTRATO || origen == null) {
            throw new IllegalStateException("El flujo de caja " + m.getId() + " (" + m.getConceptoCaja()
                    + ") necesita su movimiento de contrato y no lo tiene: origen "
                    + m.getOrigenTipo() + "/" + m.getOrigenId());
        }
        return origen;
    }
}
