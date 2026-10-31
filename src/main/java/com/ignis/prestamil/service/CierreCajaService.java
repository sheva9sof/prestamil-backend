package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ConflictException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.CajaArranque;
import com.ignis.prestamil.model.CierreCajaDiario;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaArranqueRepository;
import com.ignis.prestamil.repository.CierreCajaDiarioRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.CierreCajaResponse;
import com.ignis.prestamil.service.calculo.CorteCajaJson;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cierre de día de caja (CAJ-06) con las reglas de Jorge (B-04, 2026-10-30):
 * <ul>
 *   <li>El gerente cierra el día en curso ({@code ROLES_PERMITIDOS_CIERRE_CAJA}).</li>
 *   <li>Un día que ya pasó sin cerrarse queda pendiente: solo lo cierra Sistemas, después de revisar el
 *       corte ({@code ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE}).</li>
 *   <li>Mientras haya un día pendiente no se abre turno.</li>
 * </ul>
 *
 * <p>Cerrar congela el {@link CorteCajaResultado} del día en {@code cierre_caja_diario}; desde entonces el
 * corte de ese día es el congelado y su saldo final es el saldo anterior del siguiente. Los días se cierran
 * en orden: solo cuentan los que tuvieron movimientos de caja o traspasos (un domingo sin operación no se
 * cierra). Todo día hasta el último cierre queda congelado ({@link CandadoCierreCaja}). Para cerrar no debe
 * haber turno abierto, y el arranque debe estar capturado por Sistemas: con el provisional de 084 el corte
 * se ve, pero no se cierra.</p>
 */
@Service
@Transactional
@Slf4j
public class CierreCajaService {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final String SIN_ARRANQUE = "Sistemas debe capturar el saldo de arranque de caja antes de cerrar días.";

    private final CajaArranqueRepository cajaArranqueRepository;
    private final CierreCajaDiarioRepository cierreCajaDiarioRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final TraspasoCajaRepository traspasoCajaRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final CorteCajaService corteCajaService;
    private final Clock clock;

    public CierreCajaService(CajaArranqueRepository cajaArranqueRepository,
                             CierreCajaDiarioRepository cierreCajaDiarioRepository,
                             MovimientoCajaRepository movimientoCajaRepository,
                             TraspasoCajaRepository traspasoCajaRepository,
                             TurnoRepository turnoRepository,
                             UsuarioRepository usuarioRepository,
                             ConfiguracionRepository configuracionRepository,
                             CorteCajaService corteCajaService,
                             Clock clock) {
        this.cajaArranqueRepository = cajaArranqueRepository;
        this.cierreCajaDiarioRepository = cierreCajaDiarioRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.traspasoCajaRepository = traspasoCajaRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.corteCajaService = corteCajaService;
        this.clock = clock;
    }

    /**
     * Estado del cierre de un día para la pantalla del corte.
     *
     * @param cierre       el cierre del día; {@code null} si sigue abierto
     * @param diaPendiente el día ya pasó, tuvo movimientos y nadie lo cerró
     * @param puedeCerrar  el rol del usuario puede cerrar este día y el día no está cerrado
     * @param avisoCierre  si {@code puedeCerrar}, lo que hoy impide cerrarlo; {@code null} si nada
     */
    public record EstadoCierre(CierreCajaResponse cierre, boolean diaPendiente, boolean puedeCerrar,
                               String avisoCierre) {
    }

    /**
     * Cierra un día de caja: congela su corte y lo deja sin operaciones.
     *
     * @param sucursalId      sucursal
     * @param fechaSolicitada día a cerrar; sin ella, hoy
     * @param username        usuario que cierra
     * @return el cierre guardado
     * @throws ConflictException         si el día ya está cerrado
     * @throws ForbiddenException        si el rol no puede cerrar ese día (un día pendiente es solo de Sistemas)
     * @throws BadRequestException       si no hay arranque capturado, el día es futuro o anterior al arranque,
     *                                   hay un día anterior pendiente o hay un turno abierto
     * @throws ResourceNotFoundException si el usuario no existe
     */
    public CierreCajaResponse cerrar(Integer sucursalId, LocalDate fechaSolicitada, String username) {
        // 1. Lo primero es bloquear el arranque: una operación de caja en curso termina antes de que el
        // cierre lea el corte, y las que lleguen después ven el día ya cerrado (CandadoCierreCaja)
        CajaArranque arranque = cajaArranqueRepository.findByIdParaActualizar(sucursalId)
                .filter(a -> Boolean.TRUE.equals(a.getConfirmado()))
                .orElseThrow(() -> new BadRequestException(SIN_ARRANQUE));
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fecha = fechaSolicitada != null ? fechaSolicitada : hoy;

        // 2. El día debe existir para la caja
        if (fecha.isAfter(hoy)) {
            throw new BadRequestException("No se puede cerrar el " + dia(fecha) + ": es un día futuro.");
        }
        if (fecha.isBefore(arranque.getFechaArranque())) {
            throw new BadRequestException("El " + dia(fecha) + " es anterior al arranque de caja ("
                    + dia(arranque.getFechaArranque()) + "): no tiene corte que cerrar.");
        }

        // 3. Doble cierre
        if (congelado(arranque, fecha)) {
            throw new ConflictException(yaCerrado(sucursalId, fecha));
        }

        // 4. El gerente cierra hoy; un día que ya pasó es un día pendiente y solo lo cierra Sistemas
        boolean pendiente = fecha.isBefore(hoy);
        if (!tieneRol(usuario, rolesParaCerrar(pendiente))) {
            throw new ForbiddenException(pendiente
                    ? "El " + dia(fecha) + " quedó pendiente: solo Sistemas puede cerrarlo, después de revisar el corte."
                    : "El usuario no tiene permiso para cerrar el día.");
        }

        // 5. Días anteriores pendientes y turno abierto
        impedimento(sucursalId, arranque, fecha).ifPresent(motivo -> {
            throw new BadRequestException(motivo);
        });

        // 6. El corte que se congela es el mismo que muestra la pantalla
        CorteCajaResultado corte = corteCajaService.calcular(sucursalId, fecha);
        if (!corte.valido()) {
            throw new BadRequestException(corte.aviso());
        }

        CierreCajaDiario cierre = new CierreCajaDiario();
        cierre.setSucursalId(sucursalId);
        cierre.setFecha(fecha);
        cierre.setSaldoAnterior(corte.saldoAnterior());
        cierre.setMinistraciones(corte.ministraciones());
        cierre.setConcentraciones(corte.concentraciones());
        cierre.setBoveda(corte.boveda());
        cierre.setDotaciones(corte.dotaciones());
        cierre.setIngresosEfectivo(corte.ingresosEfectivo());
        cierre.setEgresosEfectivo(corte.egresosEfectivo());
        cierre.setRecolecciones(corte.recolecciones());
        cierre.setDiferencia(corte.diferencia());
        cierre.setSaldoFinal(corte.saldoFinal());
        cierre.setCorteJson(CorteCajaJson.escribir(corte));
        cierre.setDiaPendiente(pendiente);
        cierre.setUsuario(usuario);
        cierre.setFechaCierre(LocalDateTime.now(clock));
        try {
            cierre = cierreCajaDiarioRepository.saveAndFlush(cierre);
        } catch (DataIntegrityViolationException e) {
            // La llave (sucursal, fecha) es el último resguardo contra dos cierres del mismo día
            throw new ConflictException("La caja del " + dia(fecha) + " ya está cerrada.");
        }
        arranque.setFechaUltimoCierre(fecha);
        cajaArranqueRepository.save(arranque);

        log.info("Cierre de caja sucursal={} fecha={} pendiente={} saldoFinal={} diferencia={} usuario={}",
                sucursalId, fecha, pendiente, corte.saldoFinal(), corte.diferencia(), usuario.getNombreUsuario());
        return toResponse(cierre);
    }

    /**
     * Primer día pendiente de cierre: ya pasó, tuvo movimientos de caja o traspasos y no está cerrado. Solo
     * cuenta desde que Sistemas captura el arranque.
     *
     * @param sucursalId sucursal
     * @return el día pendiente más antiguo, o vacío si no hay
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> diaPendiente(Integer sucursalId) {
        return cajaArranqueRepository.findById(sucursalId)
                .filter(a -> Boolean.TRUE.equals(a.getConfirmado()))
                .flatMap(a -> primerDiaPorCerrar(sucursalId, a, LocalDate.now(clock)));
    }

    /**
     * Valida que se pueda abrir turno (B-04): ni hoy está cerrado ni hay un día pendiente. Se llama antes
     * de abrir el turno; el pase de almoneda no depende de esto (corre al arrancar y a las 00:05).
     *
     * @param sucursalId sucursal del turno
     * @throws BadRequestException si la caja de hoy ya se cerró o hay un día pendiente de cierre
     */
    @Transactional(readOnly = true)
    public void validarAperturaTurno(Integer sucursalId) {
        Optional<CajaArranque> arranque = cajaArranqueRepository.findById(sucursalId)
                .filter(a -> Boolean.TRUE.equals(a.getConfirmado()));
        if (arranque.isEmpty()) {
            return;
        }
        LocalDate hoy = LocalDate.now(clock);
        if (congelado(arranque.get(), hoy)) {
            throw new BadRequestException("La caja de hoy (" + dia(hoy) + ") ya se cerró: no se puede abrir "
                    + "turno hasta mañana.");
        }
        Optional<LocalDate> pendiente = primerDiaPorCerrar(sucursalId, arranque.get(), hoy);
        if (pendiente.isPresent()) {
            throw new BadRequestException("La caja del " + dia(pendiente.get()) + " quedó pendiente de cierre. "
                    + "Sistemas debe revisar el corte y cerrar ese día antes de abrir turno.");
        }
    }

    /**
     * Si un día ya quedó congelado por un cierre, sin bloquear nada: es para avisar en pantalla. El rechazo
     * de las operaciones lo hace {@link CandadoCierreCaja}.
     *
     * @param sucursalId sucursal
     * @param fecha      día
     * @return {@code true} si el día está cerrado
     */
    @Transactional(readOnly = true)
    public boolean diaCerrado(Integer sucursalId, LocalDate fecha) {
        return cajaArranqueRepository.findById(sucursalId).map(a -> congelado(a, fecha)).orElse(false);
    }

    /**
     * Estado del cierre de un día para la pantalla del corte.
     *
     * @param sucursalId sucursal
     * @param fecha      día del corte mostrado
     * @param corte      el corte de ese día
     * @param username   usuario que consulta
     * @return cierre, si es día pendiente, si el usuario puede cerrarlo y qué se lo impide
     */
    @Transactional(readOnly = true)
    public EstadoCierre estado(Integer sucursalId, LocalDate fecha, CorteCajaResultado corte, String username) {
        Optional<CierreCajaDiario> cierre = cierreCajaDiarioRepository.findBySucursalIdAndFecha(sucursalId, fecha);
        Optional<CajaArranque> arranque = cajaArranqueRepository.findById(sucursalId);
        boolean confirmado = arranque.map(CajaArranque::getConfirmado).orElse(false);
        boolean cerrado = cierre.isPresent() || arranque.map(a -> congelado(a, fecha)).orElse(false);
        LocalDate hoy = LocalDate.now(clock);
        boolean pendiente = fecha.isBefore(hoy);

        boolean diaPendiente = !cerrado && pendiente && confirmado && corte.valido()
                && primerDiaConMovimientos(sucursalId, fecha, fecha.plusDays(1)).isPresent();
        boolean puedeCerrar = !cerrado && corte.valido() && usuarioTieneRol(username, rolesParaCerrar(pendiente));
        String aviso = null;
        if (puedeCerrar) {
            aviso = confirmado ? impedimento(sucursalId, arranque.get(), fecha).orElse(null) : SIN_ARRANQUE;
        }
        return new EstadoCierre(cierre.map(this::toResponse).orElse(null), diaPendiente, puedeCerrar, aviso);
    }

    /** Lo que impide cerrar un día: otro anterior pendiente o un turno abierto. */
    private Optional<String> impedimento(Integer sucursalId, CajaArranque arranque, LocalDate fecha) {
        Optional<LocalDate> anterior = primerDiaPorCerrar(sucursalId, arranque, fecha);
        if (anterior.isPresent()) {
            return Optional.of("Primero hay que cerrar el " + dia(anterior.get())
                    + ", que quedó pendiente (lo cierra Sistemas).");
        }
        Optional<Turno> turno = turnoRepository.findByActivo(true);
        if (turno.isPresent()) {
            Turno t = turno.get();
            String de = t.getUsuario() != null ? " de " + t.getUsuario().getNombreUsuario() : "";
            String desde = t.getFechaInicio() != null ? ", abierto el " + t.getFechaInicio().format(DD_MM_YYYY_HH_MM) : "";
            return Optional.of("Hay un turno abierto (#" + t.getId() + de + desde
                    + "): primero hay que cerrar el turno y después el día.");
        }
        return Optional.empty();
    }

    /** Primer día con movimientos sin cerrar antes de {@code hasta} (exclusivo). */
    private Optional<LocalDate> primerDiaPorCerrar(Integer sucursalId, CajaArranque arranque, LocalDate hasta) {
        LocalDate desde = arranque.getFechaUltimoCierre() != null
                && !arranque.getFechaUltimoCierre().isBefore(arranque.getFechaArranque())
                ? arranque.getFechaUltimoCierre().plusDays(1)
                : arranque.getFechaArranque();
        return primerDiaConMovimientos(sucursalId, desde, hasta);
    }

    /** Primer día del periodo con flujos de caja o traspasos; {@code hasta} es exclusivo. */
    private Optional<LocalDate> primerDiaConMovimientos(Integer sucursalId, LocalDate desde, LocalDate hasta) {
        if (!desde.isBefore(hasta)) {
            return Optional.empty();
        }
        LocalDateTime flujo = movimientoCajaRepository.primerFlujo(sucursalId, desde.atStartOfDay(), hasta.atStartOfDay());
        LocalDate traspaso = traspasoCajaRepository.primerDiaConTraspasos(sucursalId, desde, hasta);
        LocalDate primerFlujo = flujo != null ? flujo.toLocalDate() : null;
        if (primerFlujo == null || (traspaso != null && traspaso.isBefore(primerFlujo))) {
            return Optional.ofNullable(traspaso);
        }
        return Optional.of(primerFlujo);
    }

    /** Todo día hasta el último cierre queda congelado. */
    private static boolean congelado(CajaArranque arranque, LocalDate fecha) {
        return arranque.getFechaUltimoCierre() != null && !fecha.isAfter(arranque.getFechaUltimoCierre());
    }

    private String yaCerrado(Integer sucursalId, LocalDate fecha) {
        return cierreCajaDiarioRepository.findBySucursalIdAndFecha(sucursalId, fecha)
                .map(c -> "La caja del " + dia(fecha) + " ya se cerró (" + c.getUsuario().getNombreUsuario()
                        + ", " + c.getFechaCierre().format(DD_MM_YYYY_HH_MM) + ").")
                .orElse("La caja del " + dia(fecha) + " ya quedó congelada por un cierre posterior.");
    }

    private CierreCajaResponse toResponse(CierreCajaDiario c) {
        return new CierreCajaResponse(c.getId(), c.getFecha(), Boolean.TRUE.equals(c.getDiaPendiente()),
                c.getDiferencia(), c.getSaldoFinal(), c.getUsuario().getNombreUsuario(), c.getFechaCierre());
    }

    private static String rolesParaCerrar(boolean pendiente) {
        return pendiente ? Constantes.ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE : Constantes.ROLES_PERMITIDOS_CIERRE_CAJA;
    }

    private boolean usuarioTieneRol(String username, String configuracion) {
        return usuarioRepository.findByNombreUsuario(username)
                .map(u -> tieneRol(u, configuracion))
                .orElse(false);
    }

    private boolean tieneRol(Usuario usuario, String configuracion) {
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        return rolId != null && rolesPermitidos(configuracion).contains(rolId);
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie puede. */
    private Set<Integer> rolesPermitidos(String configuracion) {
        return configuracionRepository.findByConfiguracion(configuracion)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }

    private static String dia(LocalDate fecha) {
        return fecha.format(DD_MM_YYYY);
    }
}
