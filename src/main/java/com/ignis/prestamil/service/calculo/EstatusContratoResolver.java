package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoOperacion;
import com.ignis.prestamil.util.Constantes;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estatus operativo del contrato y acciones disponibles (matriz RN-16). Sin estado: lo usan el
 * motor de cotización, el listado de operaciones (F2) y el pase diario (F11).
 *
 * <p>El estatus se deriva de las fechas para que sea correcto aunque el pase diario no haya corrido;
 * los estatus persistidos de cierre (finiquitado, cancelado, vendido) y el de las partidas mandan
 * sobre las fechas.</p>
 */
public final class EstatusContratoResolver {

    /**
     * Mensaje que ve el cajero cuando el contrato ya tuvo un movimiento hoy (RN-29, C-01). Se expone
     * en la cotización y como título del tooltip del botón deshabilitado en el frontend.
     */
    public static final String MOTIVO_UNO_POR_DIA =
            "Este contrato ya tuvo un movimiento hoy. Para hacer otro, cancele el anterior.";

    /** Acciones de cobro que se bloquean cuando ya hubo un movimiento del día (RN-29, C-01). */
    private static final Set<AccionContrato> ACCIONES_DE_COBRO = EnumSet.of(
            AccionContrato.REFRENDO, AccionContrato.FINIQUITO, AccionContrato.ABONO_CAPITAL,
            AccionContrato.REFRENDO_PARCIAL, AccionContrato.REFRENDO_EXTEMPORANEO,
            AccionContrato.FINIQUITO_EXTEMPORANEO);

    private EstatusContratoResolver() {
    }

    /**
     * Estatus operativo del contrato en una fecha.
     *
     * @param contrato   contrato con estatus, fechas y partidas
     * @param diasGracia días de gracia efectivos (snapshot > vigente)
     * @param hoy        fecha de consulta
     * @return el estatus operativo
     */
    public static EstatusOperativo estatusDerivado(Contrato contrato, int diasGracia, LocalDate hoy) {
        EstatusContrato persistido = contrato.getEstatus();
        if (persistido == EstatusContrato.FINIQUITADO) {
            return EstatusOperativo.FINIQUITADO;
        }
        if (persistido == EstatusContrato.CANCELADO) {
            return EstatusOperativo.CANCELADO;
        }
        // Basta una partida vendida o apartada para bloquear todo el contrato (RN-17)
        if (persistido == EstatusContrato.VENDIDO || tienePartida(contrato, EstatusPartida.VEN)) {
            return EstatusOperativo.VENDIDO;
        }
        if (tienePartida(contrato, EstatusPartida.APA)) {
            return EstatusOperativo.APARTADO;
        }
        // Pase a venta (diario o anticipado) ya registrado
        if (persistido == EstatusContrato.EN_VENTA) {
            return EstatusOperativo.EN_VENTA;
        }
        LocalDate comercializacion = contrato.getFechaComercializacion() != null
                ? contrato.getFechaComercializacion()
                : contrato.getFechaVencimiento().plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION);
        return estatusPorFechas(contrato.getFechaVencimiento(), comercializacion, diasGracia, hoy);
    }

    /**
     * Estatus que dictan solo las fechas: vigente hasta el vencimiento, en gracia hasta vencimiento +
     * gracia, vencido hasta la víspera de la comercialización y en venta desde ella (RN-04, RN-08).
     */
    public static EstatusOperativo estatusPorFechas(LocalDate vencimiento, LocalDate comercializacion,
                                                    int diasGracia, LocalDate hoy) {
        if (!hoy.isAfter(vencimiento)) {
            return EstatusOperativo.VIGENTE;
        }
        if (!hoy.isAfter(vencimiento.plusDays(diasGracia))) {
            return EstatusOperativo.EN_GRACIA;
        }
        if (hoy.isBefore(comercializacion)) {
            return EstatusOperativo.VENCIDO;
        }
        return EstatusOperativo.EN_VENTA;
    }

    /**
     * Acciones que se pueden ejecutar sobre el contrato según la matriz RN-16.
     *
     * @param estatus                estatus operativo del contrato
     * @param periodosTranscurridos  periodos que cobraría un refrendo completo hoy
     * @return las acciones habilitadas
     */
    public static Set<AccionContrato> accionesDisponibles(EstatusOperativo estatus, int periodosTranscurridos) {
        return accionesDisponibles(estatus, periodosTranscurridos, false);
    }

    /**
     * Acciones disponibles según la matriz RN-16, quitando las que refrendan si el contrato ya alcanzó
     * el máximo de refrendos de su plazo (RN-28): solo queda finiquitar.
     *
     * @param estatus                estatus operativo del contrato
     * @param periodosTranscurridos  periodos que cobraría un refrendo completo hoy
     * @param refrendosAgotados      resultado de {@link #refrendosAgotados}
     * @return las acciones habilitadas
     */
    public static Set<AccionContrato> accionesDisponibles(EstatusOperativo estatus, int periodosTranscurridos,
                                                          boolean refrendosAgotados) {
        return accionesDisponibles(estatus, periodosTranscurridos, refrendosAgotados, false);
    }

    /**
     * Igual que {@link #accionesDisponibles(EstatusOperativo, int, boolean)}, pero bloqueando además
     * los cobros si el contrato ya tuvo un movimiento del día (RN-29, C-01): refrendo, finiquito,
     * abono a capital, parcial y los extemporáneos salen del conjunto; reposición, consulta y
     * cancelación siguen disponibles (RE no cuenta — TODO G-01).
     *
     * @param estatus                 estatus operativo del contrato
     * @param periodosTranscurridos   periodos que cobraría un refrendo completo hoy
     * @param refrendosAgotados       resultado de {@link #refrendosAgotados}
     * @param yaTuvoMovimientoHoy     true si existe un movimiento no cancelado hoy cuyo tipo cuenta
     *                                para la regla ({@link com.ignis.prestamil.model.TipoMovimiento#CUENTAN_UNO_POR_DIA})
     * @return las acciones habilitadas
     */
    public static Set<AccionContrato> accionesDisponibles(EstatusOperativo estatus, int periodosTranscurridos,
                                                          boolean refrendosAgotados,
                                                          boolean yaTuvoMovimientoHoy) {
        Set<AccionContrato> acciones = accionesPorEstatus(estatus, periodosTranscurridos);
        if (refrendosAgotados) {
            acciones.removeAll(EnumSet.of(AccionContrato.REFRENDO, AccionContrato.REFRENDO_EXTEMPORANEO,
                    AccionContrato.REFRENDO_PARCIAL, AccionContrato.ABONO_CAPITAL));
        }
        if (yaTuvoMovimientoHoy) {
            acciones.removeAll(ACCIONES_DE_COBRO);
        }
        return acciones;
    }

    /**
     * Si el contrato ya no admite más refrendos (RN-28). {@code num_max_refrendos = 0} es sin límite
     * (alhajas); un valor mayor limita según el tipo de prenda y la sucursal (electrónicos).
     *
     * @param contrato  contrato con su número de refrendos
     * @param parametro parámetro vigente del plazo/tipo de prenda/sucursal; null = sin límite
     * @return true si el contrato alcanzó el máximo
     */
    public static boolean refrendosAgotados(Contrato contrato, PlazoParametro parametro) {
        Integer maximo = parametro != null ? parametro.getNumMaxRefrendos() : null;
        return maximo != null && maximo > 0 && contrato.getNumRefrendos() >= maximo;
    }

    private static Set<AccionContrato> accionesPorEstatus(EstatusOperativo estatus, int periodosTranscurridos) {
        Set<AccionContrato> acciones = EnumSet.of(AccionContrato.CONSULTA, AccionContrato.CANCELACION);
        boolean operable = true;
        switch (estatus) {
            case VIGENTE, EN_GRACIA -> acciones.addAll(EnumSet.of(AccionContrato.REFRENDO,
                    AccionContrato.FINIQUITO, AccionContrato.ABONO_CAPITAL, AccionContrato.REPOSICION));
            case VENCIDO, EN_VENTA -> acciones.addAll(EnumSet.of(AccionContrato.REFRENDO_EXTEMPORANEO,
                    AccionContrato.FINIQUITO_EXTEMPORANEO, AccionContrato.REPOSICION));
            default -> operable = false;
        }
        // El parcial deja al menos un periodo sin pagar (RN-14)
        if (operable && periodosTranscurridos >= 2) {
            acciones.add(AccionContrato.REFRENDO_PARCIAL);
        }
        return acciones;
    }

    /**
     * Botón de la matriz que corresponde a la operación pedida: refrendo y finiquito sobre un contrato
     * vencido o en venta son los extemporáneos.
     */
    public static AccionContrato accionPara(TipoOperacion operacion, EstatusOperativo estatus) {
        boolean extemporaneo = estatus == EstatusOperativo.VENCIDO || estatus == EstatusOperativo.EN_VENTA;
        return switch (operacion) {
            case REFRENDO -> extemporaneo ? AccionContrato.REFRENDO_EXTEMPORANEO : AccionContrato.REFRENDO;
            case FINIQUITO -> extemporaneo ? AccionContrato.FINIQUITO_EXTEMPORANEO : AccionContrato.FINIQUITO;
            case ABONO_CAPITAL -> AccionContrato.ABONO_CAPITAL;
            case REFRENDO_PARCIAL -> AccionContrato.REFRENDO_PARCIAL;
        };
    }

    private static boolean tienePartida(Contrato contrato, EstatusPartida estatus) {
        return contrato.getPartidas() != null
                && contrato.getPartidas().stream().anyMatch(p -> p.getEstatus() == estatus);
    }
}
