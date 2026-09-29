package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.FolioNotaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.request.MovimientoRequest;
import com.ignis.prestamil.request.PagoRequest;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.CotizacionMovimientoResponse;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.CotizacionMovimiento;
import com.ignis.prestamil.service.calculo.DesgloseCobro;
import com.ignis.prestamil.service.calculo.EstatusContratoResolver;
import com.ignis.prestamil.service.calculo.ParametrosCalculo;
import com.ignis.prestamil.service.calculo.SituacionPeriodos;
import com.ignis.prestamil.util.NumeroALetras;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gestiona los movimientos de un contrato: refrendos (normales, en gracia y extemporáneos), abonos a
 * capital, refrendos parciales, finiquitos y reposición de contrato. Todo cobro pasa por
 * {@link #registrar}, que recalcula con el motor único ({@link CalculoContratoService#cotizar}) y
 * registra el movimiento contra el turno activo para que aparezca en el corte de caja.
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
    private final FolioNotaRepository folioNotaRepository;
    private final CalculoContratoService calculoContratoService;
    private final CobroService cobroService;
    private final Clock clock;

    public MovimientoContratoService(MovimientoContratoRepository movimientoRepository,
                                     ContratoRepository contratoRepository,
                                     PlazoParametroRepository plazoParametroRepository,
                                     TurnoRepository turnoRepository,
                                     UsuarioRepository usuarioRepository,
                                     FolioNotaRepository folioNotaRepository,
                                     CalculoContratoService calculoContratoService,
                                     CobroService cobroService,
                                     Clock clock) {
        this.movimientoRepository = movimientoRepository;
        this.contratoRepository = contratoRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.folioNotaRepository = folioNotaRepository;
        this.calculoContratoService = calculoContratoService;
        this.cobroService = cobroService;
        this.clock = clock;
    }

    /**
     * Registra un movimiento con cobro. Es el único camino de escritura de refrendos, abonos a capital,
     * refrendos parciales y finiquitos: recalcula todo con la fecha del servidor (RN-19), valida la
     * operación contra la matriz RN-16 y el máximo de refrendos (RN-28), exige turno activo (RN-21),
     * valida el pago (RN-24) y actualiza el contrato en la misma transacción. El tipo resultante (RF,
     * RPG, RX…) lo decide la fecha, no el cliente.
     *
     * @param request  operación, forma de pago e identificador de idempotencia
     * @param username usuario que cobra
     * @return el movimiento registrado; si el requestId ya se usó, el movimiento de esa primera petición
     * @throws BadRequestException       si no hay turno activo, la operación no está disponible, el importe
     *                                   cambió desde la cotización o el pago no es válido
     * @throws ResourceNotFoundException si el contrato o el usuario no existen
     */
    public MovimientoResponse registrar(MovimientoRequest request, String username) {
        // El bloqueo va primero: serializa cobros simultáneos del mismo contrato y hace que una petición
        // repetida vea el movimiento que registró la anterior
        Contrato contrato = contratoRepository.findWithLockById(request.getContratoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contrato no encontrado: " + request.getContratoId()));

        // Idempotencia: el mismo requestId devuelve lo ya registrado y no cobra dos veces
        Optional<MovimientoContrato> registrado = movimientoRepository.findByRequestId(request.getRequestId());
        if (registrado.isPresent()) {
            MovimientoContrato previo = registrado.get();
            if (!previo.getContrato().getId().equals(contrato.getId())) {
                throw new BadRequestException("El identificador de la operación ya se usó en otro contrato");
            }
            log.info("Cobro repetido requestId={} contrato={}: se devuelve el movimiento {}",
                    request.getRequestId(), contrato.getFolio(), previo.getId());
            return toResponse(previo, contrato);
        }

        Turno turno = turnoActivo();
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        // Mismo cálculo y mismas validaciones que la cotización, con la fecha del servidor
        LocalDate hoy = LocalDate.now(clock);
        PlazoParametro param = obtenerParametro(contrato);
        ParametrosCalculo p = calculoContratoService.resolverParametros(contrato, param);
        validarOperacion(contrato, param, p, request.getTipoOperacion(), hoy);
        CotizacionMovimiento c = calculoContratoService.cotizar(contrato, p, request.getTipoOperacion(),
                hoy, request.getPeriodos(), request.getAbonoCapital());

        // El cajero cobra lo que vio: si el importe cambió (cambio de día, gracia rebasada) se vuelve a cotizar
        if (request.getTotalCotizado() != null && request.getTotalCotizado().compareTo(c.total()) != 0) {
            throw new BadRequestException("El importe cambió desde la cotización ($" + request.getTotalCotizado()
                    + " → $" + c.total() + "). Vuelva a cotizar la operación.");
        }

        MovimientoContrato mov = nuevoMovimiento(contrato, turno, usuario, c);
        mov.setRequestId(request.getRequestId());
        mov.setObservaciones(request.getObservaciones());
        // Solo el endpoint deprecado /refrendo llega sin pago: no tiene ventana de Cobro
        PagoRequest pago = request.getPago() != null ? request.getPago() : pagoExactoEnEfectivo(c.total());
        cobroService.aplicarPago(mov, pago, c.total());

        registrarEstadoAnterior(mov, contrato);
        aplicarAlContrato(contrato, c);
        registrarEstadoNuevo(mov, contrato);
        mov.setFolioNota(siguienteFolioNota(contrato.getSucursalId()));

        movimientoRepository.save(mov);
        contratoRepository.save(contrato);

        log.info("Movimiento {} contrato={} folioNota={} periodos={}+{} interes={} sancion={} iva={} total={}",
                mov.getTipo(), contrato.getFolio(), mov.getFolioNota(), c.periodosNormalesAplicados(),
                c.periodosExtemporaneosAplicados(), mov.getInteres(), mov.getSancion(), mov.getIva(), mov.getMonto());
        return toResponse(mov, contrato);
    }

    /**
     * Registra un refrendo por el endpoint anterior a F3: con abono es ABONO_CAPITAL y sin abono REFRENDO.
     * Sin ventana de Cobro, se registra como pago exacto en efectivo y sin idempotencia.
     *
     * @param request  contrato, abono opcional y observaciones
     * @param username usuario que registra el movimiento
     * @return el movimiento registrado
     * @deprecated usar {@link #registrar}; se conserva mientras algún cliente use POST /api/movimientos/refrendo
     */
    @Deprecated
    public MovimientoResponse refrendar(RefrendoRequest request, String username) {
        BigDecimal abono = request.getAbonoCapital();
        boolean conAbono = abono != null && abono.signum() > 0;

        MovimientoRequest r = new MovimientoRequest();
        r.setContratoId(request.getIdContrato());
        r.setTipoOperacion(conAbono ? TipoOperacion.ABONO_CAPITAL : TipoOperacion.REFRENDO);
        r.setAbonoCapital(conAbono ? abono : null);
        r.setObservaciones(request.getObservaciones());
        r.setRequestId(UUID.randomUUID().toString());
        return registrar(r, username);
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
        Turno turno = turnoActivo();
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
        mov.setFecha(LocalDateTime.now(clock));
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
     * la operación esté entre las acciones disponibles del estatus actual (RN-16, RN-28).
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
        PlazoParametro param = obtenerParametro(contrato);
        ParametrosCalculo p = calculoContratoService.resolverParametros(contrato, param);
        // La fecha de operación es siempre la del servidor
        LocalDate hoy = LocalDate.now(clock);

        Disponibilidad disponibilidad = validarOperacion(contrato, param, p, request.getTipoOperacion(), hoy);
        CotizacionMovimiento c = calculoContratoService.cotizar(contrato, p, request.getTipoOperacion(),
                hoy, request.getPeriodos(), request.getAbonoCapital());
        return toCotizacionResponse(contrato, disponibilidad.estatus(), disponibilidad.acciones(), c);
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

    /** Estatus operativo y acciones habilitadas del contrato en la fecha de operación. */
    private record Disponibilidad(EstatusOperativo estatus, Set<AccionContrato> acciones) {
    }

    /**
     * Valida la operación contra la matriz RN-16 y el máximo de refrendos (RN-28). La cotización y el
     * registro usan esta misma validación, así que lo que se cotiza es lo que se puede cobrar.
     */
    private Disponibilidad validarOperacion(Contrato contrato, PlazoParametro param, ParametrosCalculo p,
                                            TipoOperacion operacion, LocalDate hoy) {
        EstatusOperativo estatus = EstatusContratoResolver.estatusDerivado(contrato, p.diasGraciaSancion(), hoy);
        int transcurridos = calculoContratoService.situacion(contrato, p, hoy).periodosTranscurridos();
        boolean agotados = EstatusContratoResolver.refrendosAgotados(contrato, param);
        Set<AccionContrato> acciones = EstatusContratoResolver.accionesDisponibles(estatus, transcurridos, agotados);

        AccionContrato accion = EstatusContratoResolver.accionPara(operacion, estatus);
        if (!acciones.contains(accion)) {
            if (agotados && operacion != TipoOperacion.FINIQUITO) {
                throw new BadRequestException("El contrato alcanzó el máximo de refrendos permitidos ("
                        + param.getNumMaxRefrendos() + "); solo se puede finiquitar");
            }
            throw new BadRequestException(
                    "La operación " + accion + " no está disponible para un contrato " + estatus);
        }
        return new Disponibilidad(estatus, acciones);
    }

    private Turno turnoActivo() {
        return turnoRepository.findByActivo(true)
                .orElseThrow(() -> new BadRequestException(
                        "No hay un turno activo. Abra un turno antes de registrar movimientos."));
    }

    /** Movimiento con los montos de la cotización; la forma de pago y el estado se agregan después. */
    private MovimientoContrato nuevoMovimiento(Contrato contrato, Turno turno, Usuario usuario,
                                               CotizacionMovimiento c) {
        DesgloseCobro d = c.desglose();
        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(usuario);
        mov.setTipo(c.tipoMovimiento());
        mov.setMonto(c.total());
        // "interes" es interés + almacenaje: no hay columna separada para el almacenaje
        mov.setInteres(d.interesTotal());
        mov.setSancion(d.sancion());
        mov.setIva(d.iva());
        mov.setAbonoCapital(c.abonoCapital());
        mov.setSemanasVencidas(c.periodosExtemporaneosAplicados());
        mov.setPeriodosNormales(c.periodosNormalesAplicados());
        mov.setDiasGraciaUsados(c.situacion().diasGraciaUsados());
        mov.setInteresPorPeriodo(c.interesPorPeriodo());
        // RN-27: se guarda tanto el porcentaje vigente aplicado como el importe descontado para
        // reconstruir la nota de COCAE (columnas "% Desc." e "Int. c/Desc.").
        mov.setPorcDescuentoInteres(c.parametros().porcDescuentoInteres() != null
                ? c.parametros().porcDescuentoInteres() : BigDecimal.ZERO);
        mov.setImporteDescuento(c.descuento());
        mov.setFecha(LocalDateTime.now(clock));
        return mov;
    }

    /**
     * Deja el contrato como indica la cotización: fechas por RN-06 y saldo nuevo; el finiquito lo cierra
     * y libera las partidas.
     */
    private void aplicarAlContrato(Contrato contrato, CotizacionMovimiento c) {
        contrato.setSaldoCapital(c.saldoNuevo());
        if (c.operacion() == TipoOperacion.FINIQUITO) {
            // Las fechas del último periodo se conservan; las prendas se entregan al cliente
            contrato.setEstatus(EstatusContrato.FINIQUITADO);
            contrato.getPartidas().forEach(partida -> partida.setEstatus(EstatusPartida.FIN));
            return;
        }
        contrato.setFechaContrato(c.fechaContratoNueva());
        contrato.setFechaVencimiento(c.fechaVencimientoNueva());
        contrato.setFechaComercializacion(c.fechaComercializacionNueva());
        contrato.setNumRefrendos(contrato.getNumRefrendos() + 1);
        contrato.setEstatus(estatusPersistido(c.estatusNuevo()));
    }

    /** EN_GRACIA no se persiste: el contrato sigue VIGENTE hasta que el pase diario lo venza. */
    private static EstatusContrato estatusPersistido(EstatusOperativo estatus) {
        return switch (estatus) {
            case VIGENTE, EN_GRACIA -> EstatusContrato.VIGENTE;
            case VENCIDO -> EstatusContrato.VENCIDO;
            case EN_VENTA -> EstatusContrato.EN_VENTA;
            case FINIQUITADO -> EstatusContrato.FINIQUITADO;
            default -> throw new IllegalStateException("Estatus inesperado tras un movimiento: " + estatus);
        };
    }

    /** Siguiente folio de nota de la sucursal; el contador queda bloqueado hasta el fin de la transacción. */
    private Integer siguienteFolioNota(Integer sucursalId) {
        FolioNota folio = folioNotaRepository.findBySucursalId(sucursalId).orElseGet(() -> {
            FolioNota nuevo = new FolioNota();
            nuevo.setSucursalId(sucursalId);
            return nuevo;
        });
        folio.setUltimoFolio(folio.getUltimoFolio() + 1);
        folioNotaRepository.save(folio);
        return folio.getUltimoFolio();
    }

    private static PagoRequest pagoExactoEnEfectivo(BigDecimal total) {
        PagoRequest pago = new PagoRequest();
        pago.setEfectivo(total);
        pago.setTarjeta(BigDecimal.ZERO);
        return pago;
    }

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
        r.setTotalConLetra(NumeroALetras.importe(c.total()));

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
        r.setFolioNota(m.getFolioNota());
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
