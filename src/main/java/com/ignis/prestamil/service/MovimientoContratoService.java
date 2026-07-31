package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.MovimientoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    public MovimientoContratoService(MovimientoContratoRepository movimientoRepository,
                                     ContratoRepository contratoRepository,
                                     PlazoParametroRepository plazoParametroRepository,
                                     TurnoRepository turnoRepository,
                                     UsuarioRepository usuarioRepository) {
        this.movimientoRepository = movimientoRepository;
        this.contratoRepository = contratoRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra un refrendo del contrato. Si la fecha actual supera el vencimiento más
     * los días de gracia, calcula la sanción por extemporaneidad y marca el movimiento
     * como REFRENDO_EXTEMPORANEO; en caso contrario es un REFRENDO normal.
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

        BigDecimal montoPrestamo = contrato.getMontoPrestamo();
        int semanasVencidas = calcularSemanasVencidas(contrato.getFechaVencimiento(), param);

        // Interés del periodo
        BigDecimal porcInteres = param != null && param.getPorcInteresTotal() != null
                ? param.getPorcInteresTotal() : BigDecimal.ZERO;
        BigDecimal interes = montoPrestamo.multiply(porcInteres).divide(CIEN, 2, RoundingMode.HALF_UP);

        // Sanción por extemporaneidad: monto * (porcSancionSemanal/100) * semanasVencidas
        BigDecimal sancion = BigDecimal.ZERO;
        boolean aplicaSancion = param != null && Boolean.TRUE.equals(param.getAplicarSancionPorPeriodo());
        if (aplicaSancion && semanasVencidas > 0) {
            BigDecimal porcSancion = param.getPorcSancionSemanal() != null
                    ? param.getPorcSancionSemanal() : BigDecimal.ZERO;
            sancion = montoPrestamo
                    .multiply(porcSancion).divide(CIEN, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(semanasVencidas))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal abono = request.getAbonoCapital() != null ? request.getAbonoCapital() : BigDecimal.ZERO;

        // Validar máximo de refrendos
        if (param != null && param.getNumMaxRefrendos() != null && param.getNumMaxRefrendos() > 0
                && contrato.getNumRefrendos() >= param.getNumMaxRefrendos()) {
            throw new BadRequestException(
                    "El contrato alcanzó el máximo de refrendos permitidos (" + param.getNumMaxRefrendos() + ")");
        }

        TipoMovimiento tipo = semanasVencidas > 0
                ? TipoMovimiento.REFRENDO_EXTEMPORANEO : TipoMovimiento.REFRENDO;

        BigDecimal total = interes.add(sancion).add(abono);

        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(usuario);
        mov.setTipo(tipo);
        mov.setMonto(total);
        mov.setInteres(interes);
        mov.setSancion(sancion);
        mov.setAbonoCapital(abono);
        mov.setSemanasVencidas(semanasVencidas);
        mov.setFecha(LocalDateTime.now());
        mov.setObservaciones(request.getObservaciones());
        movimientoRepository.save(mov);

        // Extender el contrato un periodo y marcarlo vigente
        int diasPorPeriodo = contrato.getPlazo().getDiasPorPeriodo();
        contrato.setFechaVencimiento(contrato.getFechaVencimiento().plusDays(diasPorPeriodo));
        contrato.setNumRefrendos(contrato.getNumRefrendos() + 1);
        contrato.setEstatus(EstatusContrato.VIGENTE);
        contratoRepository.save(contrato);

        log.info("Refrendo {} contrato={} interes={} sancion={} semanas={}",
                tipo, contrato.getFolio(), interes, sancion, semanasVencidas);

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
        mov.setTipo(TipoMovimiento.REPOSICION_CONTRATO);
        mov.setMonto(monto);
        mov.setInteres(BigDecimal.ZERO);
        mov.setSancion(BigDecimal.ZERO);
        mov.setAbonoCapital(BigDecimal.ZERO);
        mov.setSemanasVencidas(0);
        mov.setFecha(LocalDateTime.now());
        mov.setObservaciones("Cobro por reposición/reimpresión de contrato");
        movimientoRepository.save(mov);

        log.info("Reposición cobrada contrato={} monto={}", contrato.getFolio(), monto);
        return toResponse(mov, contrato);
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
     * Calcula las semanas de extemporaneidad descontando los días de gracia.
     * Devuelve 0 si el contrato no está vencido más allá de la gracia.
     */
    private int calcularSemanasVencidas(LocalDate fechaVencimiento, PlazoParametro param) {
        int diasGracia = param != null && param.getDiasGraciaSinInteres() != null
                ? param.getDiasGraciaSinInteres() : 0;
        long diasVencido = ChronoUnit.DAYS.between(fechaVencimiento, LocalDate.now()) - diasGracia;
        if (diasVencido <= 0) {
            return 0;
        }
        return (int) Math.ceil(diasVencido / 7.0);
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

    private MovimientoResponse toResponse(MovimientoContrato m, Contrato contrato) {
        MovimientoResponse r = new MovimientoResponse();
        r.setId(m.getId());
        r.setIdContrato(contrato.getId());
        r.setFolioContrato(contrato.getFolio());
        r.setTipo(m.getTipo());
        r.setMonto(m.getMonto());
        r.setInteres(m.getInteres());
        r.setSancion(m.getSancion());
        r.setSemanasVencidas(m.getSemanasVencidas());
        r.setFecha(m.getFecha());
        r.setObservaciones(m.getObservaciones());
        r.setNumRefrendos(contrato.getNumRefrendos());
        r.setNuevaFechaVencimiento(contrato.getFechaVencimiento().atStartOfDay());
        return r;
    }
}
