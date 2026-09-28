package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoOperacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de {@link CalculoContratoService#cotizar}: cuánto cuesta la operación en la fecha dada
 * y cómo queda el contrato. No se persiste; el registro del movimiento (F3) recalcula con este mismo
 * método y nunca confía en montos enviados por el cliente (RN-19).
 *
 * @param operacion                      operación pedida
 * @param tipoMovimiento                 código resultante (RF, RPG, RX, RC, RP, RPX, FI, FX)
 * @param parametros                     parámetros efectivos usados (snapshot > vigente)
 * @param situacion                      periodos transcurridos y atraso en la fecha de operación
 * @param periodosMaximos                máximo que se puede pagar: transcurridos − 1 en parcial,
 *                                       transcurridos en las demás operaciones
 * @param periodosAplicados              periodos que se cobran (en parcial, ya ajustados al máximo)
 * @param periodosNormalesAplicados      de los aplicados, cuántos son normales
 * @param periodosExtemporaneosAplicados de los aplicados, cuántos son extemporáneos (se cubren primero)
 * @param interesPorPeriodo              saldo × (porcInteres + porcAlmacen) / 100, sin redondear a 2
 * @param desglose                       interés, almacenaje, sanción e IVA sobre el saldo capital
 * @param descuento                      descuento sobre intereses (RN-27); 0 hasta F4
 * @param abonoCapital                   abono a capital (sin IVA); 0 si la operación no es abono
 * @param capital                        saldo que se liquida en un finiquito; 0 en las demás
 * @param total                          total a cobrar: desglose.total + capital + abonoCapital
 * @param saldoNuevo                     saldo capital después de la operación
 * @param fechaContratoNueva             inicio del nuevo periodo (RN-06); null si el contrato se cierra
 * @param fechaVencimientoNueva          nuevo vencimiento; null si el contrato se cierra
 * @param fechaComercializacionNueva     nuevo vencimiento + 15 días (RN-08); null si el contrato se cierra
 * @param estatusNuevo                   estatus del contrato después de la operación, en la fecha dada
 * @param advertencias                   avisos para el cajero (p. ej. periodos ajustados al máximo)
 */
public record CotizacionMovimiento(
        TipoOperacion operacion,
        TipoMovimiento tipoMovimiento,
        ParametrosCalculo parametros,
        SituacionPeriodos situacion,
        int periodosMaximos,
        int periodosAplicados,
        int periodosNormalesAplicados,
        int periodosExtemporaneosAplicados,
        BigDecimal interesPorPeriodo,
        DesgloseCobro desglose,
        BigDecimal descuento,
        BigDecimal abonoCapital,
        BigDecimal capital,
        BigDecimal total,
        BigDecimal saldoNuevo,
        LocalDate fechaContratoNueva,
        LocalDate fechaVencimientoNueva,
        LocalDate fechaComercializacionNueva,
        EstatusOperativo estatusNuevo,
        List<String> advertencias
) {}
