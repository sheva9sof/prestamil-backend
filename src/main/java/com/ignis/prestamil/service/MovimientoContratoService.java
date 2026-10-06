package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.BitacoraRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.FolioNotaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CancelarMovimientoRequest;
import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.request.MovimientoRequest;
import com.ignis.prestamil.request.PagoRequest;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.request.ReposicionRequest;
import com.ignis.prestamil.util.Constantes;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ConfiguracionRepository configuracionRepository;
    private final BitacoraRepository bitacoraRepository;
    private final CalculoContratoService calculoContratoService;
    private final CobroService cobroService;
    private final MovimientoCajaService movimientoCajaService;
    private final com.ignis.prestamil.service.calculo.ParametrosSistemaCache parametrosSistemaCache;
    private final Clock clock;

    public MovimientoContratoService(MovimientoContratoRepository movimientoRepository,
                                     ContratoRepository contratoRepository,
                                     PlazoParametroRepository plazoParametroRepository,
                                     TurnoRepository turnoRepository,
                                     UsuarioRepository usuarioRepository,
                                     FolioNotaRepository folioNotaRepository,
                                     ConfiguracionRepository configuracionRepository,
                                     BitacoraRepository bitacoraRepository,
                                     CalculoContratoService calculoContratoService,
                                     CobroService cobroService,
                                     MovimientoCajaService movimientoCajaService,
                                     com.ignis.prestamil.service.calculo.ParametrosSistemaCache parametrosSistemaCache,
                                     Clock clock) {
        this.movimientoRepository = movimientoRepository;
        this.contratoRepository = contratoRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.folioNotaRepository = folioNotaRepository;
        this.configuracionRepository = configuracionRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.calculoContratoService = calculoContratoService;
        this.cobroService = cobroService;
        this.movimientoCajaService = movimientoCajaService;
        this.parametrosSistemaCache = parametrosSistemaCache;
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

        // C-07: cada cobro deja huella en caja — base del corte de caja. El concepto marca la parte
        // con tarjeta cuando aplica para que el corte distinga ingreso de efectivo real vs tarjeta.
        if (mov.getMonto() != null && mov.getMonto().signum() > 0) {
            movimientoCajaService.registrar(
                    turno,
                    contrato.getSucursalId(),
                    TipoMovimientoCaja.ENTRADA,
                    conceptoCobro(mov, contrato),
                    mov.getMonto(),
                    usuario,
                    mov);
        }

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
     * Registra la reposición/reimpresión de un contrato (F9). El importe sale del plazo
     * ({@code porc_reposicion} sobre el préstamo o {@code monto_reposicion} fijo según
     * {@code reposicion_es_porcentaje}); si {@code noCobrar = true} el importe es 0, pero siempre queda
     * un movimiento RE con folio, usuario que exentó y comentario para auditoría. La casilla
     * "No cobrar" solo la aceptan los roles configurados en {@link Constantes#ROLES_PERMITIDOS_EXENTAR_REPOSICION}
     * (por defecto Gerente y Sistemas); cualquier otro rol responde 403. La reposición no cambia el contrato.
     *
     * @param contratoId identificador del contrato
     * @param request    exención, comentario, forma de pago e identificador de idempotencia
     * @param username   usuario que registra el movimiento
     * @return el movimiento registrado; si el requestId ya se usó, el movimiento de esa primera petición
     * @throws BadRequestException       si el plazo no tiene habilitado el cobro, no hay turno activo o
     *                                   el pago no cubre el importe
     * @throws ForbiddenException        si un rol no autorizado intenta exentar el cobro
     * @throws ResourceNotFoundException si el contrato o el usuario no existen
     */
    public MovimientoResponse cobrarReposicion(Long contratoId, ReposicionRequest request, String username) {
        // Bloqueo primero: serializa reposiciones simultáneas y hace que un reintento vea la primera
        Contrato contrato = contratoRepository.findWithLockById(contratoId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + contratoId));

        Optional<MovimientoContrato> registrado = movimientoRepository.findByRequestId(request.getRequestId());
        if (registrado.isPresent()) {
            MovimientoContrato previo = registrado.get();
            if (!previo.getContrato().getId().equals(contrato.getId())) {
                throw new BadRequestException("El identificador de la operación ya se usó en otro contrato");
            }
            log.info("Reposición repetida requestId={} contrato={}: se devuelve el movimiento {}",
                    request.getRequestId(), contrato.getFolio(), previo.getId());
            return toResponse(previo, contrato);
        }

        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        // La exención se valida antes de tocar caja o folios: si no puede, nada cambia
        if (request.isNoCobrar()) {
            validarRolPuedeExentar(usuario);
        }

        PlazoParametro param = obtenerParametro(contrato);
        if (param == null || !Boolean.TRUE.equals(param.getCobrarReposicionContrato())) {
            throw new BadRequestException("El plazo no tiene habilitado el cobro de reposición de contrato");
        }

        BigDecimal importe = request.isNoCobrar()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : calcularImporteReposicion(contrato, param);

        // C-04: en reposicion por monto fijo se desglosa el IVA — Jorge lo pidio para el corte de
        // caja (30-sep). La reposicion por porcentaje queda sin IVA desglosado (fuera del scope de
        // C-04). Con noCobrar=true o porcentaje, iva=0 y total=importe.
        DesgloseIvaReposicion desglose = calcularDesgloseIvaReposicion(importe, param, request.isNoCobrar());

        Turno turno = turnoActivo();

        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(usuario);
        mov.setTipo(TipoMovimiento.RE);
        mov.setMonto(desglose.total);
        mov.setInteres(BigDecimal.ZERO);
        mov.setSancion(BigDecimal.ZERO);
        mov.setIva(desglose.iva);
        mov.setAbonoCapital(BigDecimal.ZERO);
        mov.setSemanasVencidas(0);
        mov.setDiasGraciaUsados(0);
        mov.setPorcDescuentoInteres(BigDecimal.ZERO);
        mov.setImporteDescuento(BigDecimal.ZERO);
        mov.setFecha(LocalDateTime.now(clock));
        mov.setRequestId(request.getRequestId());
        mov.setObservaciones(observacionesReposicion(request));

        // Un importe 0 no exige ventana de Cobro; con importe > 0 se valida el pago (RN-24)
        PagoRequest pago = request.getPago() != null ? request.getPago() : pagoExactoEnEfectivo(desglose.total);
        cobroService.aplicarPago(mov, pago, desglose.total);

        // La reposición no cambia el contrato: estado anterior y nuevo son iguales
        registrarEstadoAnterior(mov, contrato);
        registrarEstadoNuevo(mov, contrato);
        // Aun exenta, el ticket lleva folio (RN-25)
        mov.setFolioNota(siguienteFolioNota(contrato.getSucursalId()));
        movimientoRepository.save(mov);

        // C-07: la reposición cobrada entra a caja. La exenta ($0) no mueve caja.
        if (mov.getMonto() != null && mov.getMonto().signum() > 0) {
            movimientoCajaService.registrar(
                    turno,
                    contrato.getSucursalId(),
                    TipoMovimientoCaja.ENTRADA,
                    conceptoCobro(mov, contrato),
                    mov.getMonto(),
                    usuario,
                    mov);
        }

        log.info("Reposición contrato={} folioNota={} total={} iva={} exenta={} usuario={}",
                contrato.getFolio(), mov.getFolioNota(), desglose.total, desglose.iva,
                request.isNoCobrar(), username);
        return toResponse(mov, contrato);
    }

    /** Importe de reposición según la configuración del plazo. */
    private BigDecimal calcularImporteReposicion(Contrato contrato, PlazoParametro param) {
        if (Boolean.TRUE.equals(param.getReposicionEsPorcentaje())) {
            BigDecimal porc = param.getPorcReposicion() != null ? param.getPorcReposicion() : BigDecimal.ZERO;
            return contrato.getMontoPrestamo().multiply(porc).divide(CIEN, 2, RoundingMode.HALF_UP);
        }
        BigDecimal monto = param.getMontoReposicion() != null ? param.getMontoReposicion() : BigDecimal.ZERO;
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    /** Desglose IVA / total de una reposición. Solo aplica al monto fijo (C-04). */
    private record DesgloseIvaReposicion(BigDecimal total, BigDecimal iva) {}

    /**
     * C-04: desglosa IVA del {@link PlazoParametro#getMontoReposicion monto fijo} de reposición.
     * Si {@code reposicion_incluye_iva=true} (default), el monto configurado es IVA incluido y
     * subtotal = total − IVA. Si es false, el monto es base y total = monto × (1 + IVA). En
     * reposición por porcentaje o exenta (importe = 0) devolvemos iva = 0 y total = importe.
     */
    private DesgloseIvaReposicion calcularDesgloseIvaReposicion(BigDecimal importe,
                                                                 PlazoParametro param, boolean noCobrar) {
        BigDecimal cero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (noCobrar || importe == null || importe.signum() == 0) {
            return new DesgloseIvaReposicion(cero, cero);
        }
        if (Boolean.TRUE.equals(param.getReposicionEsPorcentaje())) {
            // Scope de C-04 estricto: solo monto fijo. Porcentaje mantiene iva=0 por ahora.
            return new DesgloseIvaReposicion(importe.setScale(2, RoundingMode.HALF_UP), cero);
        }
        BigDecimal ivaPorc = parametrosSistemaCache.getIvaPorcentaje();
        BigDecimal factor = BigDecimal.ONE.add(ivaPorc.movePointLeft(2));
        if (parametrosSistemaCache.isReposicionIncluyeIva()) {
            // Total = monto; IVA = total − total/factor (redondeado a centavos). El subtotal se
            // deriva como total − iva al emitir el ticket, así la suma siempre cuadra.
            BigDecimal subtotalCrudo = importe.divide(factor, 10, RoundingMode.HALF_UP);
            BigDecimal iva = importe.subtract(subtotalCrudo).setScale(2, RoundingMode.HALF_UP);
            return new DesgloseIvaReposicion(importe.setScale(2, RoundingMode.HALF_UP), iva);
        }
        // Monto es base: IVA se suma encima; total = base + iva.
        BigDecimal iva = importe.multiply(ivaPorc).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        return new DesgloseIvaReposicion(importe.add(iva).setScale(2, RoundingMode.HALF_UP), iva);
    }

    /** Verifica que el rol del usuario esté en {@code ROLES_PERMITIDOS_EXENTAR_REPOSICION}. */
    private void validarRolPuedeExentar(Usuario usuario) {
        Set<Integer> permitidos = rolesPermitidosExentar();
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        if (rolId == null || !permitidos.contains(rolId)) {
            throw new ForbiddenException(
                    "El usuario no tiene permiso para exentar el cobro de reposición de contrato");
        }
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie puede exentar. */
    private Set<Integer> rolesPermitidosExentar() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_EXENTAR_REPOSICION)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(java.util.Collections::emptySet);
    }

    private static String observacionesReposicion(ReposicionRequest request) {
        String base = request.isNoCobrar()
                ? "Reposición de contrato exenta"
                : "Cobro por reposición/reimpresión de contrato";
        String comentario = request.getComentario();
        if (comentario == null || comentario.isBlank()) {
            return base;
        }
        String limpio = comentario.trim();
        String texto = base + ". " + limpio;
        // observaciones admite máximo 300 caracteres (columna VARCHAR(300))
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
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
        return toCotizacionResponse(contrato, disponibilidad, c);
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

    /**
     * Cancela un movimiento restaurando el estado previo del contrato (F10, RN-26). El movimiento
     * nunca se borra: queda marcado con {@code cancelado = true}, usuario, fecha y motivo. Cada
     * movimiento guarda su {@code *Anterior} al registrarse (F0), así que la reversión es la misma
     * para todos los tipos: solo el finiquito requiere volver a poner las partidas en operación.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Solo el último movimiento no cancelado del contrato.</li>
     *   <li>Movimiento del día actual (RN-26): días anteriores modificarían cortes y bóveda.</li>
     *   <li>El turno donde se registró debe seguir activo — un turno por sucursal, así que
     *       cierre de turno = cierre de día.</li>
     *   <li>Rol del usuario en {@code ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO} (por defecto Gerente).</li>
     *   <li>Motivo obligatorio, texto libre, mínimo 10 caracteres útiles.</li>
     * </ul>
     *
     * @param movimientoId identificador del movimiento a cancelar
     * @param request      motivo de la cancelación
     * @param username     usuario que cancela (debe tener rol permitido)
     * @return el movimiento marcado como cancelado
     * @throws BadRequestException       si no es el último, ya está cancelado, es de otro día, el turno
     *                                   ya cerró o el motivo no es válido
     * @throws ForbiddenException        si el rol del usuario no puede cancelar
     * @throws ResourceNotFoundException si el movimiento o el usuario no existen
     */
    public MovimientoResponse cancelar(Long movimientoId, CancelarMovimientoRequest request, String username) {
        MovimientoContrato mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado: " + movimientoId));

        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        // C-03: las reposiciones no se cancelan por nadie — ni siquiera Sistemas. La hoja ya se
        // consumió al imprimir, así que la reposición "entra porque entra". Se rechaza antes de
        // validar rol para que el mensaje sea el mismo para gerente y sistemas.
        // FUTURO: reversión de RE solo por rol SISTEMAS con motivo (falla de máquina, corte de luz).
        if (mov.getTipo() == TipoMovimiento.RE) {
            throw new BadRequestException("Las reposiciones de contrato no se pueden cancelar");
        }

        validarRolPuedeCancelar(usuario);
        validarMotivo(request.getMotivo());

        if (Boolean.TRUE.equals(mov.getCancelado())) {
            throw new BadRequestException("El movimiento ya está cancelado");
        }

        // RN-26 + C-03: solo el último no cancelado del contrato, ignorando los RE (una reposición
        // posterior no debe bloquear la cancelación del EMP u otro movimiento previo).
        Contrato contrato = mov.getContrato();
        MovimientoContrato ultimo = movimientoRepository
                .findFirstByContratoIdAndCanceladoFalseAndTipoNotOrderByFechaDescIdDesc(
                        contrato.getId(), TipoMovimiento.RE)
                .orElseThrow(() -> new BadRequestException("El contrato no tiene un movimiento vigente que cancelar"));
        if (!ultimo.getId().equals(mov.getId())) {
            throw new BadRequestException(
                    "Solo se puede cancelar el último movimiento no cancelado del contrato");
        }

        LocalDate hoy = LocalDate.now(clock);
        if (mov.getFecha() == null || !mov.getFecha().toLocalDate().equals(hoy)) {
            throw new BadRequestException(
                    "Solo se pueden cancelar movimientos del día en curso; para días anteriores contacte a Sistemas");
        }

        // Un solo turno por sucursal: si el turno donde se cobró ya cerró, el día ya cerró
        Turno turnoMov = mov.getTurno();
        if (turnoMov == null || !Boolean.TRUE.equals(turnoMov.getActivo())) {
            throw new BadRequestException(
                    "El turno del movimiento ya cerró; no se puede cancelar tras el cierre de día");
        }

        // Snapshot del contrato ANTES de revertir (para bitácora)
        String valorViejo = snapshotContrato(contrato);

        revertirContrato(contrato, mov);

        // Cancelar sin borrar
        mov.setCancelado(true);
        mov.setUsuarioCancela(usuario);
        mov.setFechaCancelacion(LocalDateTime.now(clock));
        mov.setMotivoCancelacion(request.getMotivo().trim());

        movimientoRepository.save(mov);
        contratoRepository.save(contrato);

        // C-06/C-07: toda cancelación mueve caja. EMP cancelado → ENTRADA (el cliente devuelve el
        // préstamo); cobro cancelado → SALIDA por el total devuelto en efectivo. La devolución al
        // cliente siempre sale de caja aunque el cobro original haya sido en tarjeta: el concepto
        // deja constancia de la parte con tarjeta (últimos 4, autorización, importe) para el corte y
        // la auditoría (TODO G-06: confirmar con Alejandro el formato de la devolución de tarjeta).
        // La reposición no se revierte (C-03): si hubo RE, su propio movimiento sigue vigente y no
        // se registra flujo de caja al cancelar.
        if (mov.getTipo() == TipoMovimiento.EMP) {
            movimientoCajaService.registrar(
                    turnoMov,
                    contrato.getSucursalId(),
                    TipoMovimientoCaja.ENTRADA,
                    "CANCELACIÓN DE CONTRATO " + nz(contrato.getFolio()) + " - Préstamo devuelto",
                    mov.getMonto(),
                    usuario,
                    mov);
        } else if (mov.getTipo() != TipoMovimiento.RE) {
            movimientoCajaService.registrar(
                    turnoMov,
                    contrato.getSucursalId(),
                    TipoMovimientoCaja.SALIDA,
                    conceptoDevolucion(mov, contrato),
                    mov.getMonto(),
                    usuario,
                    mov);
        }

        registrarBitacora(username, valorViejo, snapshotContrato(contrato), mov);

        log.info("Cancelación mov={} contrato={} tipo={} usuario={} motivo={}",
                mov.getId(), contrato.getFolio(), mov.getTipo(), username, request.getMotivo());
        return toResponse(mov, contrato);
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    /**
     * Concepto del ENTRADA de caja al registrar un cobro (C-07). Para un cobro mixto deja constancia
     * de la parte con tarjeta (últimos 4, autorización, importe) para que el corte de caja distinga
     * efectivo real vs tarjeta sin tener que cruzar con el movimiento de contrato. Columna
     * {@code concepto} tope 120 caracteres.
     */
    private static String conceptoCobro(MovimientoContrato mov, Contrato contrato) {
        String tipo = mov.getTipo() != null ? mov.getTipo().getEtiqueta().toUpperCase() : "MOVIMIENTO";
        String folio = mov.getFolioNota() != null ? mov.getFolioNota().toString() : nz(contrato.getFolio());
        StringBuilder sb = new StringBuilder("COBRO ").append(tipo)
                .append(" FOLIO ").append(folio)
                .append(" CONTRATO ").append(nz(contrato.getFolio()));
        BigDecimal tarjeta = mov.getImporteTarjeta();
        if (tarjeta != null && tarjeta.signum() > 0) {
            sb.append("; TARJETA ****").append(nz(mov.getTarjetaUltimos4()))
                    .append(" AUT ").append(nz(mov.getAutorizacionBanco()))
                    .append(" $").append(tarjeta.toPlainString());
        }
        return sb.length() > 120 ? sb.substring(0, 120) : sb.toString();
    }

    /**
     * Concepto del SALIDA de caja al cancelar un cobro (C-07). Para un cobro mixto deja constancia
     * de la parte pagada con tarjeta (últimos 4, autorización, importe) porque el cliente recibe
     * efectivo pero el cobro original tocó dos medios; el corte y la auditoría necesitan separarlos.
     * El límite de 120 caracteres de la columna {@code concepto} nos obliga a recortar.
     */
    private static String conceptoDevolucion(MovimientoContrato mov, Contrato contrato) {
        String tipo = mov.getTipo() != null ? mov.getTipo().getEtiqueta().toUpperCase() : "MOVIMIENTO";
        String folio = mov.getFolioNota() != null ? mov.getFolioNota().toString() : nz(contrato.getFolio());
        StringBuilder sb = new StringBuilder("DEVOLUCIÓN POR CANCELACIÓN DE ").append(tipo)
                .append(" FOLIO ").append(folio);
        BigDecimal tarjeta = mov.getImporteTarjeta();
        if (tarjeta != null && tarjeta.signum() > 0) {
            sb.append("; TARJETA ****").append(nz(mov.getTarjetaUltimos4()))
                    .append(" AUT ").append(nz(mov.getAutorizacionBanco()))
                    .append(" $").append(tarjeta.toPlainString());
        }
        return sb.length() > 120 ? sb.substring(0, 120) : sb.toString();
    }

    /** Restaura en el contrato lo que este movimiento cambió. */
    private void revertirContrato(Contrato contrato, MovimientoContrato mov) {
        if (mov.getSaldoAnterior() != null) {
            contrato.setSaldoCapital(mov.getSaldoAnterior());
        }
        if (mov.getFechaContratoAnterior() != null) {
            contrato.setFechaContrato(mov.getFechaContratoAnterior());
        }
        if (mov.getFechaVencAnterior() != null) {
            contrato.setFechaVencimiento(mov.getFechaVencAnterior());
            contrato.setFechaComercializacion(
                    mov.getFechaVencAnterior().plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION));
        }
        if (mov.getEstatusAnterior() != null) {
            contrato.setEstatus(mov.getEstatusAnterior());
        }
        if (mov.getNumRefrendosAnterior() != null) {
            contrato.setNumRefrendos(mov.getNumRefrendosAnterior());
        }
        // Un finiquito cerró el contrato y marcó las partidas como FIN: al revertir vuelven a OP
        if (mov.getTipo() == TipoMovimiento.FI || mov.getTipo() == TipoMovimiento.FX) {
            if (contrato.getPartidas() != null) {
                contrato.getPartidas().forEach(p -> {
                    if (p.getEstatus() == EstatusPartida.FIN) {
                        p.setEstatus(EstatusPartida.OP);
                    }
                });
            }
        }
        // C-06: cancelar el EMP = cancelar el contrato. Las prendas se devuelven al cliente, así
        // que las partidas en operación pasan a CAN (bóveda las ve "devueltas"). Las que ya salieron
        // de operación por otra vía (VEN, APA) no deberían existir en un EMP del día, pero las
        // dejamos como estén para no sobreescribir estados finales.
        if (mov.getTipo() == TipoMovimiento.EMP && contrato.getPartidas() != null) {
            contrato.getPartidas().forEach(p -> {
                if (p.getEstatus() == EstatusPartida.OP) {
                    p.setEstatus(EstatusPartida.CAN);
                }
            });
        }
    }

    private void validarMotivo(String motivo) {
        if (motivo == null || motivo.trim().length() < 10) {
            throw new BadRequestException(
                    "El motivo de la cancelación es obligatorio y debe tener al menos 10 caracteres");
        }
    }

    private void validarRolPuedeCancelar(Usuario usuario) {
        Set<Integer> permitidos = rolesPermitidosCancelar();
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        if (rolId == null || !permitidos.contains(rolId)) {
            throw new ForbiddenException("El usuario no tiene permiso para cancelar movimientos");
        }
    }

    private Set<Integer> rolesPermitidosCancelar() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(java.util.Collections::emptySet);
    }

    private static String snapshotContrato(Contrato c) {
        return String.format(
                "contrato=%s saldo=%s fechaContrato=%s vencimiento=%s comercializacion=%s estatus=%s numRefrendos=%s",
                c.getFolio(), c.getSaldoCapital(), c.getFechaContrato(), c.getFechaVencimiento(),
                c.getFechaComercializacion(), c.getEstatus(), c.getNumRefrendos());
    }

    private void registrarBitacora(String username, String valorViejo, String valorNuevo, MovimientoContrato mov) {
        Bitacora b = new Bitacora();
        b.setNombreUsuario(username);
        b.setFecha(LocalDateTime.now(clock));
        b.setTipoMov("CANCELACION_MOVIMIENTO");
        b.setValorViejo(valorViejo + " movimiento=" + mov.getId() + " tipo=" + mov.getTipo());
        b.setValorNuevo(valorNuevo + " motivo=" + mov.getMotivoCancelacion());
        bitacoraRepository.save(b);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /** Estatus operativo y acciones habilitadas del contrato en la fecha de operación. */
    private record Disponibilidad(EstatusOperativo estatus, Set<AccionContrato> acciones,
                                  boolean yaTuvoMovimientoHoy) {
    }

    /**
     * Valida la operación contra la matriz RN-16, el máximo de refrendos (RN-28) y la regla
     * "un movimiento por contrato por día" (RN-29, C-01). La cotización y el registro usan la misma
     * validación, así que lo que se cotiza es lo que se puede cobrar.
     */
    private Disponibilidad validarOperacion(Contrato contrato, PlazoParametro param, ParametrosCalculo p,
                                            TipoOperacion operacion, LocalDate hoy) {
        EstatusOperativo estatus = EstatusContratoResolver.estatusDerivado(contrato, p.diasGraciaSancion(), hoy);
        int transcurridos = calculoContratoService.situacion(contrato, p, hoy).periodosTranscurridos();
        boolean agotados = EstatusContratoResolver.refrendosAgotados(contrato, param);
        boolean yaTuvoMovimientoHoy = yaTuvoMovimientoHoy(contrato.getId(), hoy);
        Set<AccionContrato> acciones = EstatusContratoResolver.accionesDisponibles(
                estatus, transcurridos, agotados, yaTuvoMovimientoHoy);

        AccionContrato accion = EstatusContratoResolver.accionPara(operacion, estatus);
        if (!acciones.contains(accion)) {
            // RN-29: el motivo del día tiene prioridad; es la causa real cuando el estatus permite la
            // operación pero ya hubo un cobro hoy
            if (yaTuvoMovimientoHoy && ACCIONES_DE_COBRO_SET.contains(accion)) {
                throw new BadRequestException(EstatusContratoResolver.MOTIVO_UNO_POR_DIA);
            }
            if (agotados && operacion != TipoOperacion.FINIQUITO) {
                throw new BadRequestException("El contrato alcanzó el máximo de refrendos permitidos ("
                        + param.getNumMaxRefrendos() + "); solo se puede finiquitar");
            }
            throw new BadRequestException(
                    "La operación " + accion + " no está disponible para un contrato " + estatus);
        }
        return new Disponibilidad(estatus, acciones, yaTuvoMovimientoHoy);
    }

    /**
     * ¿Existe un movimiento no cancelado hoy en el contrato cuyo tipo cuente para la regla
     * "un movimiento por día" (RN-29)? El reloj inyectado decide "hoy".
     */
    private boolean yaTuvoMovimientoHoy(Long contratoId, LocalDate hoy) {
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime finExclusivo = hoy.plusDays(1).atStartOfDay();
        return movimientoRepository
                .existsByContratoIdAndCanceladoFalseAndTipoInAndFechaGreaterThanEqualAndFechaLessThan(
                        contratoId, TipoMovimiento.CUENTAN_UNO_POR_DIA, inicio, finExclusivo);
    }

    private static final Set<AccionContrato> ACCIONES_DE_COBRO_SET = java.util.EnumSet.of(
            AccionContrato.REFRENDO, AccionContrato.FINIQUITO, AccionContrato.ABONO_CAPITAL,
            AccionContrato.REFRENDO_PARCIAL, AccionContrato.REFRENDO_EXTEMPORANEO,
            AccionContrato.FINIQUITO_EXTEMPORANEO);

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

    private CotizacionMovimientoResponse toCotizacionResponse(Contrato contrato, Disponibilidad disponibilidad,
                                                              CotizacionMovimiento c) {
        SituacionPeriodos s = c.situacion();
        DesgloseCobro d = c.desglose();
        CotizacionMovimientoResponse r = new CotizacionMovimientoResponse();
        r.setContratoId(contrato.getId());
        r.setFolio(contrato.getFolio());
        r.setTipoOperacion(c.operacion());
        r.setTipoMovimiento(c.tipoMovimiento());

        r.setEstatusActual(disponibilidad.estatus());
        r.setAccionesDisponibles(disponibilidad.acciones());
        if (disponibilidad.yaTuvoMovimientoHoy()) {
            r.setMotivoAccionesDeshabilitadas(EstatusContratoResolver.MOTIVO_UNO_POR_DIA);
        }
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
