package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.BuscarContratoPor;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.FiltroEstatusOperacion;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.ContratoSpecifications;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.response.ContratoOperacionDetalleResponse;
import com.ignis.prestamil.response.ContratoOperacionResponse;
import com.ignis.prestamil.response.PageResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.EstatusContratoResolver;
import com.ignis.prestamil.service.calculo.ParametrosCalculo;
import com.ignis.prestamil.service.calculo.SituacionPeriodos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consulta de contratos para la pantalla de Finiquitos y Refrendos (F2): listado con filtros y
 * detalle con las acciones habilitadas. Solo lectura; el estatus, los periodos y el interés salen de
 * {@link EstatusContratoResolver} y {@link CalculoContratoService}, no se recalculan aquí.
 */
@Service
@Transactional(readOnly = true)
public class ContratoOperacionService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "id");
    private static final DateTimeFormatter FECHA_DMY =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Estatus en los que el contrato todavía se puede cobrar y tiene sentido contar periodos. */
    private static final Set<EstatusOperativo> OPERABLES = EnumSet.of(
            EstatusOperativo.VIGENTE, EstatusOperativo.EN_GRACIA,
            EstatusOperativo.VENCIDO, EstatusOperativo.EN_VENTA);

    private final ContratoRepository contratoRepository;
    private final PlazoParametroRepository plazoParametroRepository;
    private final MovimientoContratoRepository movimientoRepository;
    private final SucursalService sucursalService;
    private final CalculoContratoService calculoContratoService;
    private final ContratoMapper contratoMapper;

    public ContratoOperacionService(ContratoRepository contratoRepository,
                                    PlazoParametroRepository plazoParametroRepository,
                                    MovimientoContratoRepository movimientoRepository,
                                    SucursalService sucursalService,
                                    CalculoContratoService calculoContratoService,
                                    ContratoMapper contratoMapper) {
        this.contratoRepository = contratoRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.movimientoRepository = movimientoRepository;
        this.sucursalService = sucursalService;
        this.calculoContratoService = calculoContratoService;
        this.contratoMapper = contratoMapper;
    }

    /**
     * Lista los contratos de la sucursal con los filtros de la pantalla, del más reciente al más antiguo.
     * El estatus de cada fila se deriva de las fechas a hoy, así que los filtros son correctos aunque el
     * pase diario no haya corrido.
     *
     * @param q         texto a buscar (opcional)
     * @param buscarPor campo contra el que se compara {@code q}
     * @param ramo      id del tipo de prenda (opcional)
     * @param estatus   filtro de estatus
     * @param page      página, desde 0
     * @param size      tamaño de página (se limita a 100)
     * @return la página de contratos con estatus y acciones disponibles
     * @throws BadRequestException si {@code q} no tiene el formato que pide {@code buscarPor}
     */
    public PageResponse<ContratoOperacionResponse> buscar(String q, BuscarContratoPor buscarPor, Integer ramo,
                                                          FiltroEstatusOperacion estatus, int page, int size) {
        LocalDate hoy = LocalDate.now();
        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, size), TAMANO_MAXIMO_PAGINA);

        Specification<Contrato> spec = Specification.where(ContratoSpecifications.deSucursal(sucursalId()))
                .and(filtroTexto(q, buscarPor))
                .and(ramo != null ? ContratoSpecifications.deRamo(ramo) : null)
                .and(ContratoSpecifications.porEstatus(estatus, hoy));
        Map<String, Optional<PlazoParametro>> parametros = new HashMap<>();

        // Gracia y vencidos dependen de los días de gracia de cada contrato: SQL trae solo los que ya
        // vencieron sin llegar a comercialización (pocos) y el resolver decide cuáles son de cada uno
        EstatusOperativo exacto = switch (estatus) {
            case PERIODO_GRACIA -> EstatusOperativo.EN_GRACIA;
            case VENCIDOS -> EstatusOperativo.VENCIDO;
            default -> null;
        };
        if (exacto != null) {
            List<ContratoOperacionResponse> filas = contratoRepository.findAll(spec, ORDEN).stream()
                    .map(c -> evaluar(c, hoy, parametros))
                    .filter(e -> e.estatus() == exacto)
                    .map(e -> llenarFila(new ContratoOperacionResponse(), e))
                    .toList();
            return PageResponse.paginar(filas, pagina, tamano);
        }

        Page<Contrato> contratos = contratoRepository.findAll(spec, PageRequest.of(pagina, tamano, ORDEN));
        return PageResponse.desdePagina(
                contratos.map(c -> llenarFila(new ContratoOperacionResponse(), evaluar(c, hoy, parametros))));
    }

    /**
     * Detalle de un contrato para operar en caja: datos del listado, cliente, periodos a hoy, partidas y
     * último movimiento vigente.
     *
     * @param id identificador del contrato
     * @return el detalle con las acciones disponibles
     * @throws ResourceNotFoundException si el contrato no existe o es de otra sucursal
     */
    public ContratoOperacionDetalleResponse detalle(Long id) {
        Contrato contrato = contratoRepository.findById(id)
                .filter(c -> c.getSucursalId().equals(sucursalId()))
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + id));
        Evaluacion e = evaluar(contrato, LocalDate.now(), new HashMap<>());

        ContratoOperacionDetalleResponse r = llenarFila(new ContratoOperacionDetalleResponse(), e);
        r.setFechaApertura(contrato.getFechaApertura());
        r.setNombreBeneficiario(contrato.getNombreBeneficiario());
        if (contrato.getCliente() != null) {
            r.setTelefonoCliente(contrato.getCliente().getTelefono());
        }
        SituacionPeriodos s = e.situacion();
        if (s != null) {
            r.setDiasAtraso(s.diasAtraso());
            r.setPeriodosTranscurridos(s.periodosTranscurridos());
            r.setPeriodosNormales(s.periodosNormales());
            r.setPeriodosExtemporaneos(s.periodosExtemporaneos());
        }
        r.setPartidas(contrato.getPartidas().stream().map(contratoMapper::toPartidaResponse).toList());
        movimientoRepository.findFirstByContratoIdAndCanceladoFalseOrderByFechaDescIdDesc(id)
                .map(this::toUltimoMovimiento)
                .ifPresent(r::setUltimoMovimiento);
        llenarReposicion(r, contrato, parametroVigente(contrato, new HashMap<>()));
        return r;
    }

    /**
     * Configuración e importe de reposición (F9). Deja los campos en null si el plazo no la habilita para
     * que el frontend pueda ocultar/deshabilitar el modal sin adivinar.
     */
    private void llenarReposicion(ContratoOperacionDetalleResponse r, Contrato contrato, PlazoParametro param) {
        if (param == null || !Boolean.TRUE.equals(param.getCobrarReposicionContrato())) {
            r.setCobrarReposicionContrato(false);
            return;
        }
        r.setCobrarReposicionContrato(true);
        r.setReposicionEsPorcentaje(param.getReposicionEsPorcentaje());
        r.setPorcReposicion(param.getPorcReposicion());
        r.setMontoReposicion(param.getMontoReposicion());
        BigDecimal importe;
        if (Boolean.TRUE.equals(param.getReposicionEsPorcentaje())) {
            BigDecimal porc = param.getPorcReposicion() != null ? param.getPorcReposicion() : BigDecimal.ZERO;
            importe = contrato.getMontoPrestamo().multiply(porc)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal monto = param.getMontoReposicion() != null ? param.getMontoReposicion() : BigDecimal.ZERO;
            importe = monto.setScale(2, RoundingMode.HALF_UP);
        }
        r.setImporteReposicion(importe);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /** Estado del contrato a una fecha, con los parámetros efectivos ya resueltos. */
    private record Evaluacion(Contrato contrato, ParametrosCalculo parametros, EstatusOperativo estatus,
                              SituacionPeriodos situacion, Set<AccionContrato> acciones) {
    }

    private Evaluacion evaluar(Contrato contrato, LocalDate hoy, Map<String, Optional<PlazoParametro>> parametros) {
        PlazoParametro vigente = parametroVigente(contrato, parametros);
        ParametrosCalculo p = calculoContratoService.resolverParametros(contrato, vigente);
        EstatusOperativo estatus = EstatusContratoResolver.estatusDerivado(contrato, p.diasGraciaSancion(), hoy);
        SituacionPeriodos s = OPERABLES.contains(estatus) ? calculoContratoService.situacion(contrato, p, hoy) : null;
        Set<AccionContrato> acciones = EstatusContratoResolver.accionesDisponibles(
                estatus, s != null ? s.periodosTranscurridos() : 0,
                EstatusContratoResolver.refrendosAgotados(contrato, vigente));
        return new Evaluacion(contrato, p, estatus, s, acciones);
    }

    private <R extends ContratoOperacionResponse> R llenarFila(R r, Evaluacion e) {
        Contrato c = e.contrato();
        r.setId(c.getId());
        r.setFolio(c.getFolio());
        r.setNombrePlazo(c.getPlazo().getNombre());
        r.setNumeroPeriodos(c.getPlazo().getNumeroPeriodos());
        r.setDiasPorPeriodo(c.getPlazo().getDiasPorPeriodo());
        List<PartidaContrato> partidas = c.getPartidas();
        r.setNumPartidas(partidas.size());
        if (!partidas.isEmpty() && partidas.get(0).getTipoPrenda() != null) {
            r.setRamo(partidas.get(0).getTipoPrenda().getTipo());
        }
        r.setEstatus(e.estatus());
        r.setNumRefrendos(c.getNumRefrendos());
        if (c.getCliente() != null) {
            r.setIdCliente(c.getCliente().getId());
            r.setNombreCliente(nombreCompleto(c.getCliente()));
        }
        r.setFechaContrato(c.getFechaContrato());
        r.setFechaVencimiento(c.getFechaVencimiento());
        r.setDiasGracia(e.parametros().diasGraciaSancion());
        r.setFechaComercializacion(c.getFechaComercializacion());
        r.setMontoPrestamo(c.getMontoPrestamo());
        r.setSaldoCapital(c.getSaldoCapital());
        r.setMontoAvaluo(c.getMontoAvaluo());
        r.setInteresPorPeriodo(calculoContratoService.interesPorPeriodo(c.getSaldoCapital(), e.parametros()));
        r.setAccionesDisponibles(e.acciones());
        return r;
    }

    private ContratoOperacionDetalleResponse.UltimoMovimiento toUltimoMovimiento(MovimientoContrato m) {
        ContratoOperacionDetalleResponse.UltimoMovimiento u = new ContratoOperacionDetalleResponse.UltimoMovimiento();
        u.setId(m.getId());
        u.setTipo(m.getTipo());
        u.setFecha(m.getFecha());
        u.setMonto(m.getMonto());
        if (m.getUsuario() != null) {
            u.setNombreUsuario(m.getUsuario().getNombreUsuario());
        }
        return u;
    }

    private Specification<Contrato> filtroTexto(String q, BuscarContratoPor buscarPor) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String texto = q.trim();
        return switch (buscarPor) {
            case CONTRATO -> ContratoSpecifications.porContrato(texto);
            case NUM_CLIENTE -> ContratoSpecifications.deCliente(numeroCliente(texto));
            case NOMBRE_CLIENTE -> ContratoSpecifications.porNombreCliente(texto);
            case FECHA_CONTRATO -> ContratoSpecifications.conFechaContrato(fecha(texto));
        };
    }

    private static Integer numeroCliente(String texto) {
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("El número de cliente debe ser numérico: " + texto);
        }
    }

    /** Acepta la fecha del input date (aaaa-mm-dd) o capturada a mano (dd/mm/aaaa). */
    private static LocalDate fecha(String texto) {
        try {
            return texto.contains("/") ? LocalDate.parse(texto, FECHA_DMY) : LocalDate.parse(texto);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Fecha de contrato inválida: " + texto + " (use dd/mm/aaaa)");
        }
    }

    /**
     * PlazoParametro vigente del contrato (plazo + tipo de prenda de la primera partida + sucursal), con
     * caché por consulta: en un listado se repiten pocas combinaciones.
     */
    private PlazoParametro parametroVigente(Contrato contrato, Map<String, Optional<PlazoParametro>> cache) {
        List<PartidaContrato> partidas = contrato.getPartidas();
        if (partidas.isEmpty() || partidas.get(0).getTipoPrenda() == null) {
            return null;
        }
        Long plazoId = contrato.getPlazo().getId();
        Integer tipoPrendaId = partidas.get(0).getTipoPrenda().getId();
        String clave = plazoId + "-" + tipoPrendaId + "-" + contrato.getSucursalId();
        return cache.computeIfAbsent(clave, k -> plazoParametroRepository
                        .findByPlazoIdAndTipoPrendaIdAndSucursalId(plazoId, tipoPrendaId, contrato.getSucursalId()))
                .orElse(null);
    }

    private Integer sucursalId() {
        // Por ahora hay una sola sucursal por instalación; los usuarios no tienen sucursal asignada
        return sucursalService.findUnique().map(Sucursal::getId)
                .orElseThrow(() -> new ResourceNotFoundException("No hay una sucursal configurada"));
    }

    private static String nombreCompleto(Cliente cliente) {
        return Stream.of(cliente.getNombre(), cliente.getApellidoPaterno(), cliente.getApellidoMaterno())
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(" "));
    }
}
