package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoOperacion;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor unico de calculo de contratos. Fuente de verdad para PDF (Jasper), amortizacion, cobro
 * real en caja ({@link com.ignis.prestamil.service.MovimientoContratoService#refrendar}) y la
 * cotizacion de movimientos ({@link #cotizar}), eliminando la divergencia historica entre motores.
 *
 * <p>Reglas confirmadas y replicadas:</p>
 * <ul>
 *   <li>Lectura de {@code porcSancionSemanal} en runtime desde el snapshot del contrato
 *       o, en su defecto, de {@link PlazoParametro} (contratos previos al changeset 026).</li>
 *   <li>Cobro por periodo = base x (porcInteres + porcAlmacen). Los gastos de administracion
 *       ("G.Oper. x Vta.") NO se cobran por periodo ni entran al IVA (GAP-09).</li>
 *   <li>Semanas de sancion: {@code Math.ceil(diasAtraso/7.0)}. La gracia solo perdona si se paga
 *       dentro de ella (RPG); rebasada, NO se descuenta del atraso (RN-05, GAP-04).</li>
 *   <li>Toggle {@code aplicarSancionPorPeriodo}: si {@code false}, sancion = 0.</li>
 *   <li>IVA leido desde {@link ParametrosSistemaCache} (id=8) con fallback 16% + log.warn.</li>
 *   <li>IVA truncado hacia abajo a 2 decimales ({@code RoundingMode.DOWN}) como COCAE.</li>
 *   <li>El abono y el capital NO llevan IVA (quedan por fuera del desglose).</li>
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
    private static final int DIAS_SEMANA = 7;

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
    public DesgloseSancion calcularSancion(BigDecimal base, ParametrosCalculo p,
                                            LocalDate fechaVencimiento, LocalDate fechaPago) {
        int diasAtraso = (int) Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, fechaPago));
        int semanas = semanasSancion(diasAtraso, p);
        return new DesgloseSancion(diasAtraso, semanas, montoSancion(base, p, semanas));
    }

    /**
     * Desglose completo de un periodo (refrendo normal o fila del PDF).
     *
     * @param base              base de calculo (prestamo en el contrato impreso)
     * @param p                 parametros efectivos ya resueltos
     * @param fechaVencimiento  fecha vigente de vencimiento del contrato (para computar atraso)
     * @param fechaPago         fecha del pago (hoy en refrendar; fecha simulada en PDF)
     * @param periodoAcumulado  1 para el siguiente periodo (refrendar); N+1, N+2... para filas
     *                          extemporaneas del PDF donde el interes y el almacen acumulan
     */
    public DesgloseCobro calcularCobroPeriodo(BigDecimal base, ParametrosCalculo p,
                                               LocalDate fechaVencimiento, LocalDate fechaPago,
                                               int periodoAcumulado) {
        DesgloseSancion s = calcularSancion(base, p, fechaVencimiento, fechaPago);
        return desglosar(base, p, periodoAcumulado, s.semanasVencidas());
    }

    /**
     * Periodos que lleva el contrato en una fecha (RN-03 a RN-05): una semana (periodo) iniciada se
     * cobra completa; dentro de la gracia no se cobran los dias extra; rebasada la gracia, los periodos
     * posteriores al vencimiento son extemporaneos.
     *
     * @param contrato contrato con fecha de contrato vigente, vencimiento y plazo
     * @param p        parametros efectivos (dias de gracia y toggle de sancion)
     * @param fecha    fecha de operacion
     * @return la situacion de periodos del contrato en esa fecha
     */
    public SituacionPeriodos situacion(Contrato contrato, ParametrosCalculo p, LocalDate fecha) {
        int diasPorPeriodo = contrato.getPlazo().getDiasPorPeriodo();
        int periodosPlazo = contrato.getPlazo().getNumeroPeriodos();
        int diasTranscurridos = (int) ChronoUnit.DAYS.between(contrato.getFechaContrato(), fecha);
        int diasAtraso = (int) Math.max(0, ChronoUnit.DAYS.between(contrato.getFechaVencimiento(), fecha));
        boolean extemporaneo = diasAtraso > p.diasGraciaSancion();

        // Una semana iniciada se cobra completa; minimo un periodo aunque el periodo pagado por
        // adelantado aun no empiece
        int periodosIniciados = Math.max(1, Math.ceilDiv(diasTranscurridos, diasPorPeriodo));

        int periodos;
        int extemporaneos;
        if (extemporaneo) {
            periodos = periodosIniciados;
            extemporaneos = Math.max(0, periodos - periodosPlazo);
        } else {
            // En gracia se cobra lo mismo que un refrendo normal: a lo mas los periodos del plazo
            periodos = Math.min(periodosIniciados, periodosPlazo);
            extemporaneos = 0;
        }
        int diasGraciaUsados = extemporaneo ? 0 : diasAtraso;

        return new SituacionPeriodos(diasTranscurridos, diasAtraso, diasGraciaUsados, extemporaneo,
                periodos, periodos - extemporaneos, extemporaneos, semanasSancion(diasAtraso, p));
    }

    /**
     * Cotiza una operacion sobre el contrato: cuanto cuesta en la fecha dada y como queda el contrato.
     * No persiste nada. La base es el saldo capital (RN-09) y las fechas siguen la regla unica RN-06:
     * nueva fecha de contrato = fecha de contrato anterior + periodos pagados, incluido el pago en gracia.
     *
     * <p>Precondicion: la operacion ya se valido contra las acciones disponibles del estatus del
     * contrato ({@link EstatusContratoResolver}); aqui solo se validan los datos de la operacion.</p>
     *
     * @param contrato             contrato a cotizar
     * @param p                    parametros efectivos ya resueltos
     * @param operacion            operacion pedida; el tipo de movimiento resultante lo decide la fecha
     * @param fechaOperacion       fecha de la operacion (la del servidor)
     * @param periodosSolicitados  periodos a cubrir; obligatorio solo en REFRENDO_PARCIAL
     * @param abonoCapital         abono a capital; solo en ABONO_CAPITAL (0 o null = aun sin capturar)
     * @return la cotizacion con desglose, fechas nuevas, estatus nuevo y advertencias
     * @throws BadRequestException si faltan periodos, no hay suficientes para un parcial o el abono no es valido
     */
    public CotizacionMovimiento cotizar(Contrato contrato, ParametrosCalculo p, TipoOperacion operacion,
                                        LocalDate fechaOperacion, Integer periodosSolicitados,
                                        BigDecimal abonoCapital) {
        SituacionPeriodos s = situacion(contrato, p, fechaOperacion);
        BigDecimal saldo = contrato.getSaldoCapital();
        int diasPorPeriodo = contrato.getPlazo().getDiasPorPeriodo();
        List<String> advertencias = new ArrayList<>();

        // Periodos a cobrar: todos los transcurridos, salvo en el parcial (RN-14)
        int maximos = s.periodosTranscurridos();
        int aplicados = s.periodosTranscurridos();
        if (operacion == TipoOperacion.REFRENDO_PARCIAL) {
            maximos = s.periodosTranscurridos() - 1;
            if (maximos < 1) {
                throw new BadRequestException(
                        "El refrendo parcial requiere al menos 2 periodos transcurridos; use Refrendar");
            }
            if (periodosSolicitados == null || periodosSolicitados < 1) {
                throw new BadRequestException("Indique cuántos periodos va a cubrir (mínimo 1)");
            }
            // Si pide de más se ajusta al máximo en vez de rechazar
            aplicados = Math.min(periodosSolicitados, maximos);
            if (aplicados < periodosSolicitados) {
                advertencias.add(String.format(
                        "Se ajustó de %d a %d periodos: en refrendo parcial el máximo es uno menos de los transcurridos",
                        periodosSolicitados, aplicados));
            }
        }

        // Se cubren primero los periodos extemporaneos (los mas antiguos) y la sancion solo por ellos
        int extemporaneosAplicados = Math.min(aplicados, s.periodosExtemporaneos());
        int normalesAplicados = aplicados - extemporaneosAplicados;
        int semanasSancion = semanasSancionCubiertas(s, extemporaneosAplicados, diasPorPeriodo);

        BigDecimal abono = BigDecimal.ZERO;
        if (operacion == TipoOperacion.ABONO_CAPITAL) {
            abono = abonoCapital != null ? abonoCapital : BigDecimal.ZERO;
            validarAbono(abono, saldo);
        }

        // El interes se calcula con el saldo anterior: el abono no reduce el interes de este periodo (RN-13)
        DesgloseCobro desglose = desglosar(saldo, p, aplicados, semanasSancion);
        BigDecimal capital = operacion == TipoOperacion.FINIQUITO ? saldo : BigDecimal.ZERO;
        BigDecimal total = desglose.total().add(capital).add(abono);
        BigDecimal saldoNuevo = saldo.subtract(capital).subtract(abono);

        // Fechas por la regla unica RN-06; el finiquito cierra el contrato
        LocalDate fechaContratoNueva = null;
        LocalDate vencimientoNuevo = null;
        LocalDate comercializacionNueva = null;
        EstatusOperativo estatusNuevo = EstatusOperativo.FINIQUITADO;
        if (operacion != TipoOperacion.FINIQUITO) {
            fechaContratoNueva = contrato.getFechaContrato().plusDays((long) aplicados * diasPorPeriodo);
            vencimientoNuevo = fechaContratoNueva.plusDays(
                    (long) contrato.getPlazo().getNumeroPeriodos() * diasPorPeriodo);
            comercializacionNueva = vencimientoNuevo.plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION);
            // Un parcial puede dejar el contrato todavia vencido
            estatusNuevo = EstatusContratoResolver.estatusPorFechas(
                    vencimientoNuevo, comercializacionNueva, p.diasGraciaSancion(), fechaOperacion);
        }

        return new CotizacionMovimiento(
                operacion,
                tipoMovimiento(operacion, s, extemporaneosAplicados),
                p, s,
                maximos, aplicados, normalesAplicados, extemporaneosAplicados,
                interesPorPeriodo(saldo, p),
                desglose,
                // TODO F4: descuento sobre intereses desde la parametrizacion (RN-27)
                BigDecimal.ZERO.setScale(2),
                abono, capital, total, saldoNuevo,
                fechaContratoNueva, vencimientoNuevo, comercializacionNueva,
                estatusNuevo,
                List.copyOf(advertencias));
    }

    /**
     * Interes + almacenaje de un periodo ("Int x Per." de COCAE, GAP-09), sin redondear a centavos
     * (RN-10). No incluye gastos de administracion ni IVA.
     *
     * @param saldo saldo capital vigente (RN-09)
     * @param p     parametros efectivos ya resueltos
     * @return el cobro de un periodo con 4 decimales
     */
    public BigDecimal interesPorPeriodo(BigDecimal saldo, ParametrosCalculo p) {
        return saldo.multiply(p.porcInteres().add(p.porcAlmacen()))
                .divide(CIEN, 4, RoundingMode.HALF_UP);
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

    /**
     * Desglose comun a todos los cobros. Cada componente se calcula con la tasa sin redondear y se
     * redondea una sola vez al final (RN-10).
     */
    private DesgloseCobro desglosar(BigDecimal base, ParametrosCalculo p, int periodos, int semanasSancion) {
        BigDecimal n = new BigDecimal(periodos);
        BigDecimal interes = pctPor(base, p.porcInteres(), n);
        BigDecimal almacen = pctPor(base, p.porcAlmacen(), n);
        BigDecimal sancion = montoSancion(base, p, semanasSancion);

        BigDecimal baseIva = interes.add(almacen).add(sancion);
        // COCAE trunca el IVA a 2 decimales (RoundingMode.DOWN), verificado con capturas.
        BigDecimal iva = baseIva.multiply(p.porcIva()).divide(CIEN, 2, RoundingMode.DOWN);

        return new DesgloseCobro(
                base.setScale(2, RoundingMode.HALF_UP),
                interes, almacen, sancion, semanasSancion,
                baseIva, iva, baseIva.add(iva));
    }

    /** La gracia solo perdona el atraso si se paga dentro de ella; rebasada, cuenta todo el atraso. */
    private int semanasSancion(int diasAtraso, ParametrosCalculo p) {
        if (!p.aplicarSancion() || diasAtraso <= p.diasGraciaSancion()) {
            return 0;
        }
        return Math.ceilDiv(diasAtraso, DIAS_SEMANA);
    }

    /**
     * Semanas de sancion que cubre un pago de {@code extemporaneosAplicados} periodos. En plazos
     * semanales es un periodo = una semana; en otros plazos se convierten los dias cubiertos a
     * semanas, sin pasar de las semanas adeudadas.
     */
    private int semanasSancionCubiertas(SituacionPeriodos s, int extemporaneosAplicados, int diasPorPeriodo) {
        if (extemporaneosAplicados == s.periodosExtemporaneos()) {
            return s.semanasSancion();
        }
        return Math.min(s.semanasSancion(), Math.ceilDiv(extemporaneosAplicados * diasPorPeriodo, DIAS_SEMANA));
    }

    private BigDecimal montoSancion(BigDecimal base, ParametrosCalculo p, int semanas) {
        if (semanas == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return base
                .multiply(p.porcSancionSemanal())
                .divide(CIEN, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(semanas))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static TipoMovimiento tipoMovimiento(TipoOperacion operacion, SituacionPeriodos s,
                                                 int extemporaneosAplicados) {
        return switch (operacion) {
            case FINIQUITO -> s.extemporaneo() ? TipoMovimiento.FX : TipoMovimiento.FI;
            case ABONO_CAPITAL -> TipoMovimiento.RC;
            case REFRENDO_PARCIAL -> extemporaneosAplicados > 0 ? TipoMovimiento.RPX : TipoMovimiento.RP;
            case REFRENDO -> s.extemporaneo() ? TipoMovimiento.RX
                    : s.diasGraciaUsados() > 0 ? TipoMovimiento.RPG : TipoMovimiento.RF;
        };
    }

    /** Abono en 0 = aun no capturado (el modal de COCAE abre asi y muestra el importe por refrendo). */
    private static void validarAbono(BigDecimal abono, BigDecimal saldo) {
        if (abono.signum() < 0) {
            throw new BadRequestException("El abono a capital no puede ser negativo");
        }
        if (abono.signum() > 0 && abono.compareTo(Constantes.ABONO_CAPITAL_MINIMO) < 0) {
            throw new BadRequestException("El abono mínimo a capital es $" + Constantes.ABONO_CAPITAL_MINIMO);
        }
        if (abono.compareTo(saldo) >= 0) {
            throw new BadRequestException("El abono cubre todo el saldo del contrato; use Finiquitar");
        }
    }

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
