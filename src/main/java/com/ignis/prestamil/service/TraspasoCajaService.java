package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.CajaMapper;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.TraspasoCajaDenominacion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.DenominacionRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CancelarTraspasoCajaRequest;
import com.ignis.prestamil.request.TraspasoCajaDenominacionRequest;
import com.ignis.prestamil.request.TraspasoCajaRequest;
import com.ignis.prestamil.response.DenominacionResponse;
import com.ignis.prestamil.response.TraspasoCajaResponse;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traspasos de dinero entre las cajas de la sucursal (CAJ-02, RC-02): ministración, concentración,
 * dotación y recolección.
 *
 * <p>El cliente solo manda el tipo, el desglose por denominación y la referencia bancaria. El servidor
 * pone las cajas (las fija el tipo), la fecha de operación, el folio y el importe, que es la suma del
 * desglose (RC-03). Ningún traspaso deja la bóveda ni la ventanilla en negativo.</p>
 */
@Service
@Transactional
@Slf4j
public class TraspasoCajaService {

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    private final TraspasoCajaRepository traspasoCajaRepository;
    private final CajaRepository cajaRepository;
    private final DenominacionRepository denominacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final TurnoRepository turnoRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final FolioService folioService;
    private final SaldoCajaService saldoCajaService;
    private final CajaMapper cajaMapper;
    private final Clock clock;

    public TraspasoCajaService(TraspasoCajaRepository traspasoCajaRepository,
                               CajaRepository cajaRepository,
                               DenominacionRepository denominacionRepository,
                               UsuarioRepository usuarioRepository,
                               TurnoRepository turnoRepository,
                               ConfiguracionRepository configuracionRepository,
                               FolioService folioService,
                               SaldoCajaService saldoCajaService,
                               CajaMapper cajaMapper,
                               Clock clock) {
        this.traspasoCajaRepository = traspasoCajaRepository;
        this.cajaRepository = cajaRepository;
        this.denominacionRepository = denominacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
        this.configuracionRepository = configuracionRepository;
        this.folioService = folioService;
        this.saldoCajaService = saldoCajaService;
        this.cajaMapper = cajaMapper;
        this.clock = clock;
    }

    /**
     * Registra un traspaso entre cajas con su desglose por denominación.
     *
     * @param sucursalId identificador de la sucursal
     * @param request    tipo, desglose, importe que muestra la pantalla y referencia bancaria opcional
     * @param username   usuario que registra
     * @return el traspaso guardado, con folio y desglose
     * @throws ForbiddenException        si el rol del usuario no puede registrar ese tipo de traspaso
     * @throws BadRequestException       si el desglose no suma el importe, trae una denominación repetida
     *                                   o inactiva, o el traspaso deja la caja de origen en negativo
     * @throws ResourceNotFoundException si el usuario o alguna de las cajas no existen
     */
    public TraspasoCajaResponse registrar(Integer sucursalId, TraspasoCajaRequest request, String username) {
        TipoTraspasoCaja tipo = request.getTipo();
        Usuario usuario = usuario(username);
        validarRol(usuario, configuracionRoles(tipo),
                "El usuario no tiene permiso para registrar " + nombre(tipo));

        // RC-02: el tipo fija las cajas
        Caja origen = caja(sucursalId, tipo.origen());
        Caja destino = caja(sucursalId, tipo.destino());

        // RC-03: el importe es la suma del desglose; el de la pantalla solo se coteja
        TraspasoCaja traspaso = new TraspasoCaja();
        armarDesglose(traspaso, request.getDesglose());
        BigDecimal importe = traspaso.getDesglose().stream()
                .map(TraspasoCajaDenominacion::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getImporte().compareTo(importe) != 0) {
            throw new BadRequestException("El importe " + pesos(request.getImporte())
                    + " no coincide con la suma del desglose " + pesos(importe));
        }

        validarSaldoSuficiente(origen, importe, "El traspaso");
        Long folio = folioService.siguiente(sucursalId, SerieFolio.TRASPASO_CAJA);

        LocalDateTime ahora = LocalDateTime.now(clock);
        traspaso.setSucursalId(sucursalId);
        traspaso.setFolio(folio);
        traspaso.setTipo(tipo);
        traspaso.setCajaOrigen(origen);
        traspaso.setCajaDestino(destino);
        traspaso.setImporte(importe);
        traspaso.setFechaOperacion(ahora.toLocalDate());
        traspaso.setFechaRegistro(ahora);
        traspaso.setUsuario(usuario);
        // Ministración y concentración las puede hacer el gerente sin turno abierto
        traspaso.setTurno(turnoRepository.findByActivo(true).orElse(null));
        traspaso.setReferenciaBancaria(normalizarReferencia(request.getReferenciaBancaria()));

        TraspasoCaja guardado = traspasoCajaRepository.save(traspaso);
        log.info("Traspaso {} folio={} sucursal={} {}→{} importe={} usuario={}",
                tipo, folio, sucursalId, origen.getClave(), destino.getClave(), importe,
                usuario.getNombreUsuario());
        return cajaMapper.toTraspasoResponse(guardado);
    }

    /**
     * Traspasos de un día de la sucursal, de todos los estatus, ordenados por folio.
     *
     * @param sucursalId identificador de la sucursal
     * @param fecha      día de operación; si es nulo, hoy
     * @param tipo       tipo de traspaso; si es nulo, todos
     * @return traspasos con su desglose
     */
    @Transactional(readOnly = true)
    public List<TraspasoCajaResponse> listar(Integer sucursalId, LocalDate fecha, TipoTraspasoCaja tipo) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(clock);
        List<TraspasoCaja> traspasos = tipo != null
                ? traspasoCajaRepository.findBySucursalIdAndFechaOperacionAndTipoOrderByFolioAsc(sucursalId, dia, tipo)
                : traspasoCajaRepository.findBySucursalIdAndFechaOperacionOrderByFolioAsc(sucursalId, dia);
        return cajaMapper.toTraspasoResponses(traspasos);
    }

    /**
     * Denominaciones que se capturan en el desglose de un traspaso y en el arqueo, de mayor a menor.
     * Las inactivas (0.01 desde Q-10) no salen: el registro las rechaza.
     *
     * @return denominaciones activas en su orden
     */
    @Transactional(readOnly = true)
    public List<DenominacionResponse> denominacionesActivas() {
        return cajaMapper.toDenominacionResponses(denominacionRepository.findByActivaTrueOrderByOrdenAsc());
    }

    /**
     * Cancela un traspaso del día: el dinero regresa a la caja de origen. El traspaso nunca se borra;
     * queda marcado con usuario, fecha y motivo.
     *
     * @param traspasoId identificador del traspaso
     * @param request    motivo de la cancelación
     * @param username   usuario que cancela
     * @return el traspaso cancelado
     * @throws ResourceNotFoundException si el traspaso o el usuario no existen
     * @throws ForbiddenException        si el rol del usuario no puede cancelar traspasos
     * @throws BadRequestException       si ya está cancelado, es de otro día, el motivo no es válido o la
     *                                   caja de destino ya no tiene el dinero
     */
    public TraspasoCajaResponse cancelar(Long traspasoId, CancelarTraspasoCajaRequest request, String username) {
        TraspasoCaja traspaso = traspasoCajaRepository.findByIdParaActualizar(traspasoId)
                .orElseThrow(() -> new ResourceNotFoundException("Traspaso no encontrado: " + traspasoId));
        Usuario usuario = usuario(username);

        // TODO A-05: cuando exista AutorizacionService (VTA-03), autorizar con token de un gerente en
        // lugar de exigir que el usuario de la sesión sea de Sistemas
        validarRol(usuario, Constantes.ROLES_PERMITIDOS_CANCELAR_TRASPASO,
                "El usuario no tiene permiso para cancelar traspasos");

        String motivo = request.getMotivo() != null ? request.getMotivo().trim() : "";
        if (motivo.length() < 10) {
            throw new BadRequestException(
                    "El motivo de la cancelación es obligatorio y debe tener al menos 10 caracteres");
        }
        if (traspaso.getEstatus() == EstatusTraspasoCaja.CANCELADO) {
            throw new BadRequestException("El traspaso ya está cancelado");
        }
        // Como RN-26: cancelar un traspaso de otro día cambiaría un corte ya hecho
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (!traspaso.getFechaOperacion().equals(ahora.toLocalDate())) {
            throw new BadRequestException("Solo se pueden cancelar traspasos del día en curso");
        }

        // El dinero regresa al origen: el destino todavía debe tenerlo
        validarSaldoSuficiente(traspaso.getCajaDestino(), traspaso.getImporte(), "La cancelación");

        traspaso.setEstatus(EstatusTraspasoCaja.CANCELADO);
        traspaso.setFechaCancelacion(ahora);
        traspaso.setUsuarioCancela(usuario);
        traspaso.setMotivoCancelacion(motivo);
        TraspasoCaja guardado = traspasoCajaRepository.save(traspaso);
        log.info("Traspaso cancelado {} folio={} sucursal={} importe={} usuario={} motivo={}",
                traspaso.getTipo(), traspaso.getFolio(), traspaso.getSucursalId(), traspaso.getImporte(),
                usuario.getNombreUsuario(), motivo);
        return cajaMapper.toTraspasoResponse(guardado);
    }

    /** Arma los renglones del desglose con el valor de cada denominación del catálogo. */
    private void armarDesglose(TraspasoCaja traspaso, List<TraspasoCajaDenominacionRequest> desglose) {
        Set<Integer> ids = new HashSet<>();
        for (TraspasoCajaDenominacionRequest renglon : desglose) {
            if (!ids.add(renglon.getDenominacionId())) {
                throw new BadRequestException("La denominación " + renglon.getDenominacionId()
                        + " viene repetida en el desglose");
            }
        }
        Map<Integer, Denominacion> denominaciones = denominacionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Denominacion::getId, Function.identity()));

        for (TraspasoCajaDenominacionRequest renglon : desglose) {
            Denominacion denominacion = denominaciones.get(renglon.getDenominacionId());
            if (denominacion == null || !Boolean.TRUE.equals(denominacion.getActiva())) {
                throw new BadRequestException("La denominación " + renglon.getDenominacionId()
                        + " no existe o no está activa");
            }
            TraspasoCajaDenominacion d = new TraspasoCajaDenominacion();
            d.setTraspaso(traspaso);
            d.setDenominacion(denominacion);
            d.setCantidad(renglon.getCantidad());
            d.setImporte(denominacion.getValor()
                    .multiply(BigDecimal.valueOf(renglon.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP));
            traspaso.getDesglose().add(d);
        }
    }

    /**
     * La caja de donde sale el dinero no puede quedar en negativo. La concentradora no se valida: es el
     * banco o la tesorería y la sucursal no lleva su saldo. La caja queda bloqueada hasta el fin de la
     * transacción, así dos salidas simultáneas de la misma caja no validan contra el mismo saldo.
     */
    private void validarSaldoSuficiente(Caja caja, BigDecimal importe, String operacion) {
        if (caja.getTipo() == TipoCaja.CONCENTRADORA) {
            return;
        }
        cajaRepository.findByIdParaActualizar(caja.getId());
        BigDecimal saldo = saldoCajaService.saldoActual(caja);
        if (saldo.compareTo(importe) < 0) {
            throw new BadRequestException(operacion + " deja " + caja.getNombre() + " en negativo: saldo "
                    + pesos(saldo) + ", importe " + pesos(importe));
        }
    }

    private Caja caja(Integer sucursalId, TipoCaja tipo) {
        return cajaRepository.findFirstBySucursalIdAndTipoAndActivaTrueOrderByClaveAsc(sucursalId, tipo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La sucursal " + sucursalId + " no tiene caja " + tipo + " activa"));
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
    }

    private void validarRol(Usuario usuario, String configuracion, String mensaje) {
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        if (rolId == null || !rolesPermitidos(configuracion).contains(rolId)) {
            throw new ForbiddenException(mensaje);
        }
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

    private static String configuracionRoles(TipoTraspasoCaja tipo) {
        return switch (tipo) {
            case MINISTRACION -> Constantes.ROLES_PERMITIDOS_MINISTRACION;
            case CONCENTRACION -> Constantes.ROLES_PERMITIDOS_CONCENTRACION;
            case DOTACION -> Constantes.ROLES_PERMITIDOS_DOTACION;
            case RECOLECCION -> Constantes.ROLES_PERMITIDOS_RECOLECCION;
        };
    }

    private static String nombre(TipoTraspasoCaja tipo) {
        return switch (tipo) {
            case MINISTRACION -> "ministraciones";
            case CONCENTRACION -> "concentraciones";
            case DOTACION -> "dotaciones";
            case RECOLECCION -> "recolecciones";
        };
    }

    private static String normalizarReferencia(String referencia) {
        return referencia == null || referencia.isBlank() ? null : referencia.trim();
    }

    private static String pesos(BigDecimal importe) {
        return String.format(MX, "$%,.2f", importe);
    }
}
