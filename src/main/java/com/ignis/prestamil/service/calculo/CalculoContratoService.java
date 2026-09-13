package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.PlazoParametro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Motor unico de calculo de contratos. Fuente de verdad para PDF (Jasper) y para el
 * cobro real en caja ({@link com.ignis.prestamil.service.MovimientoContratoService#refrendar}),
 * eliminando la divergencia historica entre ambos motores.
 *
 * <p>Reglas confirmadas y replicadas:</p>
 * <ul>
 *   <li>Lectura de {@code porcSancionSemanal} en runtime desde el snapshot del contrato
 *       o, en su defecto, de {@link PlazoParametro} (contratos previos al changeset 026).</li>
 *   <li>Redondeo de semanas vencidas: {@code Math.ceil((diasAtraso)/7.0)}.</li>
 *   <li>Base de la sancion: monto del prestamo.</li>
 *   <li>Toggle {@code aplicarSancionPorPeriodo}: si {@code false}, sancion = 0.</li>
 *   <li>IVA leido desde {@link ParametrosSistemaCache} (id=8) con fallback 16% + log.warn.</li>
 *   <li>IVA truncado hacia abajo a 2 decimales ({@code RoundingMode.DOWN}) como COCAE.</li>
 *   <li>El abono de capital NO lleva IVA (queda por fuera; el llamador lo suma).</li>
 * </ul>
 *
 * <p><b>Snapshot vs vigente</b>: un contrato firmado no debe cambiar de montos al
 * reimprimirse (PROFECO). {@link #resolverParametros} usa cada campo del snapshot del
 * contrato si esta presente, o el valor vigente ({@code PlazoParametro} +
 * {@code parametros_sistema}) como fallback documentado para contratos previos al
 * changeset 026.</p>
 */
@Service
@Slf4j
public class CalculoContratoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ParametrosSistemaCache parametrosSistemaCache;

    public CalculoContratoService(ParametrosSistemaCache parametrosSistemaCache) {
        this.parametrosSistemaCache = parametrosSistemaCache;
    }

    // =========================================================================
    // API publica
    // =========================================================================

    /**
     * Resuelve los parametros efectivos para el motor: snapshot del contrato si esta
     * presente, o configuracion vigente como fallback.
     *
     * @param contrato          contrato del que se toma el snapshot (los siete {@code snap_*})
     * @param parametroVigente  PlazoParametro vigente para el plazo/tipo/sucursal del contrato
     *                          (puede ser null; en ese caso se devuelven ceros seguros)
     * @return los parametros efectivos que el motor debe usar
     */
    public ParametrosCalculo resolverParametros(Contrato contrato, PlazoParametro parametroVigente) {
        BigDecimal porcInteres     = coalesce(contrato.getSnapPorcInteres(),
                                              getOrZero(parametroVigente, PlazoParametro::getPorcInteres));
        BigDecimal porcAlmacen     = coalesce(contrato.getSnapPorcAlmacen(),
                                              getOrZero(parametroVigente, PlazoParametro::getPorcAlmacen));
        BigDecimal porcGastosAdmin = coalesce(contrato.getSnapPorcGastosAdmin(),
                                              getOrZero(parametroVigente, PlazoParametro::getPorcGastosAdmin));
        BigDecimal porcSancion     = coalesce(contrato.getSnapPorcSancionSemanal(),
                                              getOrZero(parametroVigente, PlazoParametro::getPorcSancionSemanal));
        int diasGracia             = coalesceInt(contrato.getSnapDiasGraciaSancion(),
                                              parametroVigente != null && parametroVigente.getDiasGraciaSinInteres() != null
                                                      ? parametroVigente.getDiasGraciaSinInteres() : 0);
        boolean aplicaSancion      = coalesceBool(contrato.getSnapAplicarSancionPeriodo(),
                                              parametroVigente != null
                                                      && Boolean.TRUE.equals(parametroVigente.getAplicarSancionPorPeriodo()));
        BigDecimal porcIva         = coalesce(contrato.getSnapIvaPorcentaje(),
                                              parametrosSistemaCache.getIvaPorcentaje());
        return new ParametrosCalculo(
                porcInteres, porcAlmacen, porcGastosAdmin,
                porcSancion, diasGracia, aplicaSancion, porcIva);
    }

    /**
     * Calcula solo la sancion por extemporaneidad. Metodo puro para tests y para
     * consumidores que ya tienen el interes calculado.
     */
    public DesgloseSancion calcularSancion(BigDecimal montoPrestamo, ParametrosCalculo p,
                                            LocalDate fechaVencimiento, LocalDate fechaPago) {
        long atrasoBruto = ChronoUnit.DAYS.between(fechaVencimiento, fechaPago);
        int diasAtraso = (int) Math.max(0, atrasoBruto - p.diasGraciaSancion());
        int semanas = diasAtraso == 0 ? 0 : (int) Math.ceil(diasAtraso / 7.0);

        BigDecimal monto;
        if (!p.aplicarSancion() || semanas == 0) {
            monto = BigDecimal.ZERO.setScale(2);
        } else {
            monto = montoPrestamo
                    .multiply(p.porcSancionSemanal())
                    .divide(CIEN, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(semanas))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // El motor reporta 0 semanas cuando el toggle esta apagado: para el caller es la senal de
        // que no aplica sancion. Los dias efectivos si se computan como metadato.
        int semanasReportadas = p.aplicarSancion() ? semanas : 0;
        return new DesgloseSancion(diasAtraso, semanasReportadas, monto);
    }

    /**
     * Desglose completo de un periodo (refrendo normal o fila del PDF).
     *
     * @param montoPrestamo     monto del prestamo del contrato
     * @param p                 parametros efectivos ya resueltos
     * @param fechaVencimiento  fecha vigente de vencimiento del contrato (para computar atraso)
     * @param fechaPago         fecha del pago (hoy en refrendar; fecha simulada en PDF)
     * @param periodoAcumulado  1 para el siguiente periodo (refrendar); N+1, N+2... para filas
     *                          extemporaneas del PDF donde el interes/almacen/gastos acumulan
     */
    public DesgloseCobro calcularCobroPeriodo(BigDecimal montoPrestamo, ParametrosCalculo p,
                                               LocalDate fechaVencimiento, LocalDate fechaPago,
                                               int periodoAcumulado) {
        BigDecimal periodo = new BigDecimal(periodoAcumulado);
        BigDecimal interes     = pctPor(montoPrestamo, p.porcInteres(),     periodo);
        BigDecimal almacen     = pctPor(montoPrestamo, p.porcAlmacen(),     periodo);
        BigDecimal gastosAdmin = pctPor(montoPrestamo, p.porcGastosAdmin(), periodo);

        DesgloseSancion s = calcularSancion(montoPrestamo, p, fechaVencimiento, fechaPago);

        BigDecimal baseIva = interes.add(almacen).add(gastosAdmin).add(s.monto());
        // COCAE trunca el IVA a 2 decimales (RoundingMode.DOWN), verificado con capturas.
        BigDecimal iva = baseIva.multiply(p.porcIva()).divide(CIEN, 2, RoundingMode.DOWN);
        BigDecimal total = baseIva.add(iva);

        return new DesgloseCobro(
                montoPrestamo.setScale(2, RoundingMode.HALF_UP),
                interes, almacen, gastosAdmin,
                s.monto(), s.semanasVencidas(),
                baseIva, iva, total);
    }

    // =========================================================================
    // Overloads de conveniencia sobre entidades
    // =========================================================================

    public DesgloseCobro calcularCobroPeriodo(Contrato contrato, PlazoParametro parametroVigente,
                                               LocalDate fechaPago, int periodoAcumulado) {
        ParametrosCalculo p = resolverParametros(contrato, parametroVigente);
        return calcularCobroPeriodo(contrato.getMontoPrestamo(), p,
                contrato.getFechaVencimiento(), fechaPago, periodoAcumulado);
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    private BigDecimal pctPor(BigDecimal base, BigDecimal porc, BigDecimal periodo) {
        return base.multiply(porc).multiply(periodo).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal coalesce(BigDecimal snapshot, BigDecimal vigente) {
        return snapshot != null ? snapshot : (vigente != null ? vigente : BigDecimal.ZERO);
    }

    private static int coalesceInt(Integer snapshot, int vigente) {
        return snapshot != null ? snapshot : vigente;
    }

    private static boolean coalesceBool(Boolean snapshot, boolean vigente) {
        return snapshot != null ? snapshot : vigente;
    }

    private static BigDecimal getOrZero(PlazoParametro p, java.util.function.Function<PlazoParametro, BigDecimal> getter) {
        if (p == null) return BigDecimal.ZERO;
        BigDecimal v = getter.apply(p);
        return v != null ? v : BigDecimal.ZERO;
    }
}
