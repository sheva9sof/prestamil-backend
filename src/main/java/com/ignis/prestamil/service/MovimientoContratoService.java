package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.CotizacionMovimientoResponse;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.CotizacionMovimiento;
import com.ignis.prestamil.service.calculo.DesgloseCobro;
import com.ignis.prestamil.service.calculo.EstatusContratoResolver;
import com.ignis.prestamil.service.calculo.ParametrosCalculo;
import com.ignis.prestamil.service.calculo.SituacionPeriodos;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Gestiona los movimientos de un contrato: refrendos (normales y extemporáneos),
 * abonos, finiquitos y reposición de contrato. Calcula intereses y la sanción por
 * extemporaneidad (porcentaje semanal configurable, 2% por defecto) y registra cada
 * movimiento contra el turno activo para que aparezca en el corte de caja.
 */
@Service
@Transactional
@Slf4j
public class MovimientoContratoService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final MovimientoContratoRepository movimientoRepository;
    private final ContratoRepository contratoRepository;
    private final PlazoParametroRepository plazoParametroRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalculoContratoService calculoContratoService;

    public MovimientoContratoService(MovimientoContratoRepository movimientoRepository,
                                     ContratoRepository contratoRepository,
                                     PlazoParametroRepository plazoParametroRepository,
                                     TurnoRepository turnoRepository,
                                     UsuarioRepository usuarioRepository,
                                     CalculoContratoService calculoContratoService) {
        this.movimientoRepository = movimientoRepository;
        this.contratoRepository = contratoRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.calculoContratoService = calculoContratoService;
    }

    /**
     * Registra un refrendo del contrato. Si la fecha actual supera el vencimiento más
     * los días de gracia, calcula la sanción por extemporaneidad y marca el movimiento
     * como RX; en caso contrario es RF, o RC si trae abono a capital.
     *
     * @param request  contrato y datos del movimiento (abono opcional)
     * @param username usuario que registra el movimiento
     * @return MovimientoResponse con interés, sanción, semanas vencidas y nueva fecha de vencimiento
     * @throws BadRequestException si no hay turno activo o se supera el máximo de refrendos
     */
    public MovimientoResponse refrendar(RefrendoRequest request, String username) {
        Contrato contrato = contratoRepository.findById(request.getIdContrato())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contrato no encontrado: " + request.getIdContrato()));
        Turno turno = turnoRepository.findByActivo(true)
                .orElseThrow(() -> new BadRequestException(
                        "No hay un turno activo. Abra un turno antes de registrar movimientos."));
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        PlazoParametro param = obtenerParametro(contrato);

        // Validar maximo de refrendos ANTES de calcular (evita side effects si falla)
        if (param != null && param.getNumMaxRefrendos() != null && param.getNumMaxRefrendos() > 0
                && contrato.getNumRefrendos() >= param.getNumMaxRefrendos()) {
            throw new BadRequestException(
                    "El contrato alcanzo el maximo de refrendos permitidos (" + param.getNumMaxRefrendos() + ")");
        }

        // Motor unico (Pasada 2): calcula interes+almacen+gastos, sancion y su IVA en una sola pasada,
        // usando el snapshot del contrato (changeset 026) si esta presente o config vigente como fallback.
        // CAMBIO DE COBRO vs. version anterior: ahora se cobra IVA sobre (interes + sancion) en vez de
        // salir del refrendar sin IVA. La sancion sigue siendo prestamo x porcSancionSemanal/100 x semanas,
        // pero al total agregado se le suma el IVA como en el contrato impreso.
        DesgloseCobro d = calculoContratoService.calcularCobroPeriodo(
                contrato, param, LocalDate.now(), 1);

        // "interes" en MovimientoContrato es el interes total (interes + almacen): no hay columna separada
        // para el almacenaje. Los gastos admin ya no se cobran por periodo (GAP-09).
        BigDecimal interesTotal = d.interesTotal();
        BigDecimal sancion = d.sancion();
        int semanasVencidas = d.semanasVencidas();

        BigDecimal abono = request.getAbonoCapital() != null ? request.getAbonoCapital() : BigDecimal.ZERO;

        TipoMovimiento tipo;
        if (semanasVencidas > 0) {
            tipo = TipoMovimiento.RX;
        } else if (abono.compareTo(BigDecimal.ZERO) > 0) {
            tipo = TipoMovimiento.RC;
        } else {
            tipo = TipoMovimiento.RF;
        }

        // total = interes+almacen+gastos + sancion + IVA(base) + abono. El abono NO lleva IVA (es capital).
        BigDecimal total = d.total().add(abono);

        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(usuario);
        mov.setTipo(tipo);
        mov.setMonto(total);
        mov.setInteres(interesTotal);
        mov.setSancion(sancion);
        mov.setAbonoCapital(abono);
        mov.setSemanasVencidas(semanasVencidas);
        mov.setIva(d.iva());
        mov.setFecha(LocalDateTime.now());
        mov.setObservaciones(request.getObservaciones());
        registrarEstadoAnterior(mov, contrato);

        // Extender el contrato un periodo y marcarlo vigente. Las fechas y el saldo solo se mantienen
        // sincronizados con las columnas del changeset 027; la regla de fechas RN-06 llega en F3.
        int diasPorPeriodo = contrato.getPlazo().getDiasPorPeriodo();
        contrato.setFechaContrato(contrato.getFechaContrato().plusDays(diasPorPeriodo));
        contrato.setFechaVencimiento(contrato.getFechaVencimiento().plusDays(diasPorPeriodo));
        contrato.setFechaComercializacion(contrato.getFechaVencimiento()
                .plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION));
        contrato.setSaldoCapital(contrato.getSaldoCapital().subtract(abono));
        contrato.setNumRefrendos(contrato.getNumRefrendos() + 1);
        contrato.setEstatus(EstatusContrato.VIGENTE);

        registrarEstadoNuevo(mov, contrato);
        movimientoRepository.save(mov);
        contratoRepository.save(contrato);

        log.info("Refrendo {} contrato={} interes={} sancion={} semanas={} total={}",
                tipo, contrato.getFolio(), interesTotal, sancion, semanasVencidas, total);

        return toResponse(mov, contrato);
    }

    /**
     * Cobra la reposición/reimpresión del contrato según la configuración del plazo
     * y registra el movimiento contra el turno activo (caja).
     *
     * @param contratoId identificador del contrato
     * @param username   usuario que cobra
     * @return MovimientoResponse del cobro de reposición
     * @throws BadRequestException si el plazo no tiene habilitado el cobro de reposición o no hay turno activo
     */
    public MovimientoResponse cobrarReposicion(Long contratoId, String username) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + contratoId));
        Turno turno = turnoRepository.findByActivo(true)
                .orElseThrow(() -> new BadRequestException(
                        "No hay un turno activo. Abra un turno antes de registrar movimientos."));
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        PlazoParametro param = obtenerParametro(contrato);
        if (param == null || !Boolean.TRUE.equals(param.getCobrarReposicionContrato())) {
            throw new BadRequestException("El plazo no tiene habilitado el cobro de reposición de contrato");
        }

        BigDecimal monto;
        if (Boolean.TRUE.equals(param.getReposicionEsPorcentaje())) {
            BigDecimal porc = param.getPorcReposicion() != null ? param.getPorcReposicion() : BigDecimal.ZERO;
            monto = contrato.getMontoPrestamo().multiply(porc).divide(CIEN, 2, RoundingMode.HALF_UP);
        } else {
            monto = param.getMontoReposicion() != null ? param.getMontoReposicion() : BigDecimal.ZERO;
        }

        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(usuario);
        mov.setTipo(TipoMovimiento.RE);
        mov.setMonto(monto);
        mov.setInteres(BigDecimal.ZERO);
        mov.setSancion(BigDecimal.ZERO);
        mov.setAbonoCapital(BigDecimal.ZERO);
        mov.setSemanasVencidas(0);
        mov.setFecha(LocalDateTime.now());
        mov.setObservaciones("Cobro por reposición/reimpresión de contrato");
        // La reposición no cambia el contrato: estado anterior y nuevo son iguales
        registrarEstadoAnterior(mov, contrato);
        registrarEstadoNuevo(mov, contrato);
        movimientoRepository.save(mov);

        log.info("Reposición cobrada contrato={} monto={}", contrato.getFolio(), monto);
        return toResponse(mov, contrato);
    }

    /**
     * Cotiza una operación sobre el contrato en la fecha del servidor, sin persistir nada. Valida que
     * la operación esté entre las acciones disponibles del estatus actual (RN-16).
     *
     * @param request contrato, operación y, según el caso, periodos o abono a capital
     * @return CotizacionMovimientoResponse con desglose, fechas nuevas y acciones disponibles
     * @throws BadRequestException si la operación no está disponible o sus datos no son válidos
     */
    @Transactional(readOnly = true)
    public CotizacionMovimientoResponse cotizar(CotizacionRequest request) {
        Contrato contrato = contratoRepository.findById(request.getContratoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contrato no encontrado: " + request.getContratoId()));
        ParametrosCalculo p = calculoContratoService.resolverParametros(contrato, obtenerParametro(contrato));
        // La fecha de operación es siempre la del servidor
        LocalDate hoy = LocalDate.now();

        // Validar la operación contra la matriz de estatus antes de calcular
        EstatusOperativo estatus = EstatusContratoResolver.estatusDerivado(contrato, p.diasGraciaSancion(), hoy);
        int transcurridos = calculoContratoService.situacion(contrato, p, hoy).periodosTranscurridos();
        Set<AccionContrato> acciones = EstatusContratoResolver.accionesDisponibles(estatus, transcurridos);
        AccionContrato accion = EstatusContratoResolver.accionPara(request.getTipoOperacion(), estatus);
        if (!acciones.contains(accion)) {
            throw new BadRequestException(
                    "La operación " + accion + " no está disponible para un contrato " + estatus);
        }

        CotizacionMovimiento c = calculoContratoService.cotizar(contrato, p, request.getTipoOperacion(),
                hoy, request.getPeriodos(), request.getAbonoCapital());
        return toCotizacionResponse(contrato, estatus, acciones, c);
    }

    /**
     * Lista los movimientos de un contrato en orden cronológico.
     *
     * @param contratoId identificador del contrato
     * @return lista de MovimientoResponse
     */
    @Transactional(readOnly = true)
    public List<MovimientoResponse> getMovimientos(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + contratoId));
        return movimientoRepository.findByContratoIdOrderByFechaAsc(contratoId)
                .stream()
                .map(m -> toResponse(m, contrato))
                .toList();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Obtiene el PlazoParametro representativo del contrato (plazo + tipo de prenda de
     * la primera partida + sucursal). Devuelve null si no hay configuración.
     */
    private PlazoParametro obtenerParametro(Contrato contrato) {
        Integer tipoPrendaId = null;
        if (contrato.getPartidas() != null && !contrato.getPartidas().isEmpty()
                && contrato.getPartidas().get(0).getTipoPrenda() != null) {
            tipoPrendaId = contrato.getPartidas().get(0).getTipoPrenda().getId();
        }
        if (tipoPrendaId == null) {
            return null;
        }
        return plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(
                contrato.getPlazo().getId(), tipoPrendaId, contrato.getSucursalId())
                .orElse(null);
    }

    /**
     * Copia al movimiento el estado del contrato ANTES de aplicarlo. Es lo que la cancelación
     * genérica (F10) restaurará.
     */
    private void registrarEstadoAnterior(MovimientoContrato mov, Contrato contrato) {
        mov.setSaldoAnterior(contrato.getSaldoCapital());
        mov.setFechaContratoAnterior(contrato.getFechaContrato());
        mov.setFechaVencAnterior(contrato.getFechaVencimiento());
        mov.setEstatusAnterior(contrato.getEstatus());
        mov.setNumRefrendosAnterior(contrato.getNumRefrendos());
    }

    /** Copia al movimiento el estado del contrato DESPUÉS de aplicarlo. */
    private void registrarEstadoNuevo(MovimientoContrato mov, Contrato contrato) {
        mov.setSaldoNuevo(contrato.getSaldoCapital());
        mov.setFechaContratoNueva(contrato.getFechaContrato());
        mov.setFechaVencNueva(contrato.getFechaVencimiento());
        mov.setEstatusNuevo(contrato.getEstatus());
    }

    private CotizacionMovimientoResponse toCotizacionResponse(Contrato contrato, EstatusOperativo estatus,
                                                              Set<AccionContrato> acciones,
                                                              CotizacionMovimiento c) {
        SituacionPeriodos s = c.situacion();
        DesgloseCobro d = c.desglose();
        CotizacionMovimientoResponse r = new CotizacionMovimientoResponse();
        r.setContratoId(contrato.getId());
        r.setFolio(contrato.getFolio());
        r.setTipoOperacion(c.operacion());
        r.setTipoMovimiento(c.tipoMovimiento());

        r.setEstatusActual(estatus);
        r.setAccionesDisponibles(acciones);
        r.setFechaContrato(contrato.getFechaContrato());
        r.setFechaVencimiento(contrato.getFechaVencimiento());
        r.setSaldoCapital(contrato.getSaldoCapital());

        r.setDiasAtraso(s.diasAtraso());
        r.setDiasGraciaUsados(s.diasGraciaUsados());
        r.setPeriodosTranscurridos(s.periodosTranscurridos());
        r.setPeriodosNormales(s.periodosNormales());
        r.setPeriodosExtemporaneos(s.periodosExtemporaneos());
        r.setPeriodosMaximos(c.periodosMaximos());
        r.setPeriodosAplicados(c.periodosAplicados());
        r.setPeriodosNormalesAplicados(c.periodosNormalesAplicados());
        r.setPeriodosExtemporaneosAplicados(c.periodosExtemporaneosAplicados());
        r.setSemanasSancion(d.semanasVencidas());

        r.setInteresPorPeriodo(c.interesPorPeriodo());
        r.setInteres(d.interes());
        r.setAlmacen(d.almacen());
        r.setInteresTotal(d.interesTotal());
        r.setPorcSancionSemanal(c.parametros().porcSancionSemanal());
        r.setSancion(d.sancion());
        r.setDescuento(c.descuento());
        r.setSubtotal(d.baseIva());
        r.setPorcIva(c.parametros().porcIva());
        r.setIva(d.iva());
        r.setAbonoCapital(c.abonoCapital());
        r.setCapital(c.capital());
        r.setTotal(c.total());

        r.setSaldoNuevo(c.saldoNuevo());
        r.setFechaContratoNueva(c.fechaContratoNueva());
        r.setFechaVencimientoNueva(c.fechaVencimientoNueva());
        r.setFechaComercializacionNueva(c.fechaComercializacionNueva());
        r.setEstatusNuevo(c.estatusNuevo());
        r.setAdvertencias(c.advertencias());
        return r;
    }

    private MovimientoResponse toResponse(MovimientoContrato m, Contrato contrato) {
        MovimientoResponse r = new MovimientoResponse();
        r.setId(m.getId());
        r.setIdContrato(contrato.getId());
        r.setFolioContrato(contrato.getFolio());
        r.setTipo(m.getTipo());
        r.setMonto(m.getMonto());
        r.setInteres(m.getInteres());
        r.setSancion(m.getSancion());
        r.setAbonoCapital(m.getAbonoCapital());
        r.setSemanasVencidas(m.getSemanasVencidas());
        r.setPeriodosNormales(m.getPeriodosNormales());
        r.setDiasGraciaUsados(m.getDiasGraciaUsados());
        r.setInteresPorPeriodo(m.getInteresPorPeriodo());
        r.setPorcDescuentoInteres(m.getPorcDescuentoInteres());
        r.setImporteDescuento(m.getImporteDescuento());
        r.setIva(m.getIva());
        r.setFecha(m.getFecha());
        r.setObservaciones(m.getObservaciones());
        if (m.getUsuario() != null) {
            r.setNombreUsuario(m.getUsuario().getNombreUsuario());
        }
        r.setNumRefrendos(contrato.getNumRefrendos());
        // En el historial cada fila muestra SU vencimiento; los movimientos previos al changeset 027
        // no lo guardaron y caen al vencimiento vigente del contrato.
        LocalDate vencimiento = m.getFechaVencNueva() != null ? m.getFechaVencNueva() : contrato.getFechaVencimiento();
        r.setNuevaFechaVencimiento(vencimiento.atStartOfDay());

        r.setImporteEfectivo(m.getImporteEfectivo());
        r.setImporteTarjeta(m.getImporteTarjeta());
        r.setTipoTarjeta(m.getTipoTarjeta());
        r.setTarjetaUltimos4(m.getTarjetaUltimos4());
        if (m.getBancoEmisor() != null) {
            r.setBancoEmisor(m.getBancoEmisor().getNombre());
        }
        r.setAutorizacionBanco(m.getAutorizacionBanco());
        r.setCambioEntregado(m.getCambioEntregado());

        r.setSaldoAnterior(m.getSaldoAnterior());
        r.setSaldoNuevo(m.getSaldoNuevo());
        r.setFechaContratoAnterior(m.getFechaContratoAnterior());
        r.setFechaVencAnterior(m.getFechaVencAnterior());
        r.setFechaContratoNueva(m.getFechaContratoNueva());
        r.setFechaVencNueva(m.getFechaVencNueva());
        r.setEstatusAnterior(m.getEstatusAnterior());
        r.setEstatusNuevo(m.getEstatusNuevo());

        r.setCancelado(m.getCancelado());
        r.setFechaCancelacion(m.getFechaCancelacion());
        if (m.getUsuarioCancela() != null) {
            r.setUsuarioCancela(m.getUsuarioCancela().getNombreUsuario());
        }
        r.setMotivoCancelacion(m.getMotivoCancelacion());
        return r;
    }
}
