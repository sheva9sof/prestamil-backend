package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.*;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.PartidaContratoRequest;
import com.ignis.prestamil.request.RecalcularPartidasRequest;
import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.PartidaRecalculadaResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.DesgloseCobro;
import com.ignis.prestamil.service.calculo.ParametrosCalculo;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@Slf4j
public class ContratoService extends BaseService<Contrato, Long, ContratoRepository> {

    private final ClienteRepository clienteRepository;
    private final PlazoRepository plazoRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TipoPrendaRepository tipoPrendaRepository;
    private final CatValorPrendaRepository catValorPrendaRepository;
    private final ContratoMapper contratoMapper;
    private final PlazoParametroRepository plazoParametroRepository;
    private final PlazoService plazoService;
    private final PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;
    private final ParametrosSistemaCache parametrosSistemaCache;
    private final CalculoContratoService calculoContratoService;
    private final MovimientoContratoRepository movimientoContratoRepository;
    private final MovimientoCajaService movimientoCajaService;
    private final BolsaService bolsaService;

    private static final List<Integer> KILATAJES_COCAE = List.of(6, 8, 10, 12, 14, 18, 21, 24);
    private static final BigDecimal LEY_925 = new BigDecimal("925");
    // Ley de plata baja: COCAE la maneja como 720 (fineness estándar 0.720). La columna de precio
    // sigue llamándose ley_725 por compatibilidad; almacena el precio por gramo de esta ley.
    private static final BigDecimal LEY_720 = new BigDecimal("720");
    // C-08 (RN-30, PLAN-CORRECCIONES-30SEP): el total prestado en partidas sujetas al redondeo
    // se baja al múltiplo de $5 más cercano. COCAE lo hace para entregar efectivo fácil de contar
    // ("no le estamos prestando 10 centavos"). El ajuste se descuenta de la última partida.
    private static final BigDecimal MULTIPLO_REDONDEO_PRESTAMO = new BigDecimal("5");
    // (IVA_PORCENTAJE eliminado en Pasada 2: el IVA lo lee CalculoContratoService del snapshot del
    // contrato o del cache de parametros_sistema con fallback 16 + log.warn.)
    private static final String MSG_PLATA_SIN_CONFIG =
            "No hay configuración de plazo para plata (plazo/tipo de prenda/sucursal); "
            + "configure el precio por gramo en Plazos y Periodos";

    public ContratoService(ContratoRepository repository,
                           ClienteRepository clienteRepository,
                           PlazoRepository plazoRepository,
                           TurnoRepository turnoRepository,
                           UsuarioRepository usuarioRepository,
                           TipoPrendaRepository tipoPrendaRepository,
                           CatValorPrendaRepository catValorPrendaRepository,
                           ContratoMapper contratoMapper,
                           PlazoParametroRepository plazoParametroRepository,
                           PlazoService plazoService,
                           PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository,
                           ParametrosSistemaCache parametrosSistemaCache,
                           CalculoContratoService calculoContratoService,
                           MovimientoContratoRepository movimientoContratoRepository,
                           MovimientoCajaService movimientoCajaService,
                           BolsaService bolsaService) {
        super(repository);
        this.clienteRepository = clienteRepository;
        this.plazoRepository = plazoRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.tipoPrendaRepository = tipoPrendaRepository;
        this.catValorPrendaRepository = catValorPrendaRepository;
        this.contratoMapper = contratoMapper;
        this.plazoParametroRepository = plazoParametroRepository;
        this.plazoService = plazoService;
        this.plazoHechuraAlhajaRepository = plazoHechuraAlhajaRepository;
        this.parametrosSistemaCache = parametrosSistemaCache;
        this.calculoContratoService = calculoContratoService;
        this.movimientoContratoRepository = movimientoContratoRepository;
        this.movimientoCajaService = movimientoCajaService;
        this.bolsaService = bolsaService;
    }

    /**
     * Registra un nuevo contrato de empeño con sus partidas de prenda.
     * Requiere un turno activo en el sistema. El folio se genera automáticamente.
     *
     * @param request  datos del contrato y sus partidas
     * @param username nombre de usuario del cajero que registra
     * @return ContratoResponse con folio asignado y partidas guardadas
     * @throws BadRequestException si no hay turno activo o las partidas están vacías
     */
    public ContratoResponse crearContrato(ContratoRequest request, String username) {
        // 1. Verificar turno activo
        Turno turno = turnoRepository.findByActivo(true)
                .orElseThrow(() -> new BadRequestException(
                        "No hay un turno activo. Abra un turno antes de registrar contratos."));

        // 2. Buscar usuario actual
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));

        // 3. Buscar cliente
        Cliente cliente = clienteRepository.findById(request.getIdCliente())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado: " + request.getIdCliente()));

        // 4. Buscar plazo
        Plazo plazo = plazoRepository.findById(request.getIdPlazo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plazo no encontrado: " + request.getIdPlazo()));

        // 5. Calcular fechas
        LocalDateTime fechaApertura = LocalDateTime.now();
        LocalDate fechaVencimiento = fechaApertura.toLocalDate()
                .plusDays((long) plazo.getDiasPorPeriodo() * plazo.getNumeroPeriodos());

        // 6. Construir partidas y acumular totales (con validación de préstamo y avalúo)
        Integer sucursalId = 1; // TODO: derivar de la sucursal del usuario/turno
        List<PartidaContrato> partidas = new ArrayList<>();
        BigDecimal totalPrestamo = BigDecimal.ZERO;
        BigDecimal totalAvaluo = BigDecimal.ZERO;

        for (int i = 0; i < request.getPartidas().size(); i++) {
            PartidaContratoRequest pr = request.getPartidas().get(i);
            PartidaContrato partida = buildPartida(pr, i + 1, plazo, sucursalId);
            totalPrestamo = totalPrestamo.add(partida.getMontoPrestamo());
            totalAvaluo = totalAvaluo.add(partida.getAvaluoContrato());
            partidas.add(partida);
        }

        // 6.b C-08 (RN-30): el total prestado en partidas sujetas al redondeo (hoy solo alhajas,
        // ver aplicaRedondeoMultiploDe5) debe ser múltiplo de $5. El frontend ya descuenta la
        // diferencia en la última partida; aquí validamos para que un cliente que lo omita no
        // pueda cerrar el contrato con centavos.
        validarRedondeoMultiploDe5(partidas);

        // 7. Construir contrato
        Contrato contrato = new Contrato();
        contrato.setCliente(cliente);
        contrato.setTurno(turno);
        contrato.setSucursalId(1);
        contrato.setPlazo(plazo);
        contrato.setUsuario(usuario);
        contrato.setFechaApertura(fechaApertura);
        contrato.setFechaContrato(fechaApertura.toLocalDate());
        contrato.setFechaVencimiento(fechaVencimiento);
        contrato.setFechaComercializacion(
                fechaVencimiento.plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION));
        contrato.setMontoPrestamo(totalPrestamo);
        contrato.setSaldoCapital(totalPrestamo);
        contrato.setMontoAvaluo(totalAvaluo);
        contrato.setEstatus(EstatusContrato.VIGENTE);
        contrato.setNumRefrendos(0);
        contrato.setTipoIdentificacion(request.getTipoIdentificacion());
        contrato.setNumIdentificacion(request.getNumIdentificacion());
        contrato.setNombreBeneficiario(request.getNombreBeneficiario());

        if (request.getIdBeneficiario() != null) {
            Cliente beneficiario = clienteRepository.findById(request.getIdBeneficiario())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Beneficiario no encontrado: " + request.getIdBeneficiario()));
            contrato.setBeneficiario(beneficiario);
        }

        // 8. Enlazar partidas al contrato
        for (PartidaContrato partida : partidas) {
            partida.setContrato(contrato);
        }
        contrato.setPartidas(partidas);

        // 8.b Snapshot de los 7 parametros de calculo vigentes (changeset 026, requisito PROFECO):
        // congelan los porcentajes/gracia/IVA al momento de firmar para que la reimpresion futura
        // del PDF y el cobro en caja se computen con los mismos numeros originales aunque la config
        // vigente cambie despues. El PlazoParametro representativo es el de la primera partida
        // (misma convencion que MovimientoContratoService.obtenerParametro); Pasada 1 solo lo persiste,
        // Pasada 2 lo consumira desde CalculoContratoService.
        aplicarSnapshotCalculo(contrato, partidas, plazo.getId(), sucursalId);

        // 8.c D-10: bolsa (consecutivo por sucursal) y código de barras de cada partida (consecutivo
        // global). Se toman en esta transacción: si el contrato falla, los contadores no avanzan.
        contrato.setNumBolsa(bolsaService.siguienteNumBolsa(sucursalId));
        bolsaService.asignarCodigosBarras(partidas);

        // 9. Primera persistencia para obtener el ID generado
        Contrato guardado = repository.save(contrato);

        // 10. Asignar folio basado en el ID y persistir de nuevo
        guardado.setFolio(String.format("CTR-%06d", guardado.getId()));
        guardado = repository.save(guardado);

        // 11. Movimiento EMP (periodo 0): primera fila del historial del contrato
        MovimientoContrato emp = registrarMovimientoEmpeno(guardado, turno, usuario);

        // 12. C-07: el préstamo entregado al cliente sale de caja. Base del corte de caja.
        if (totalPrestamo != null && totalPrestamo.signum() > 0) {
            movimientoCajaService.registrar(
                    turno,
                    guardado.getSucursalId(),
                    TipoMovimientoCaja.SALIDA,
                    "PRÉSTAMO CONTRATO " + guardado.getFolio(),
                    totalPrestamo,
                    usuario,
                    emp);
        }

        log.info("Contrato creado: {} | bolsa={} | cliente={} | monto={}", guardado.getFolio(),
                guardado.getNumBolsa(), cliente.getId(), totalPrestamo);

        return contratoMapper.toResponse(guardado);
    }

    /**
     * Obtiene un contrato por su folio.
     *
     * @param folio folio del contrato (ej. CTR-000001)
     * @return ContratoResponse con sus partidas
     * @throws ResourceNotFoundException si el folio no existe
     */
    @Transactional(readOnly = true)
    public ContratoResponse getByFolio(String folio) {
        Contrato contrato = repository.findByFolio(folio)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + folio));
        return contratoMapper.toResponse(contrato);
    }

    /**
     * Obtiene un contrato por su ID con sus partidas.
     *
     * @param id identificador del contrato
     * @return ContratoResponse con sus partidas
     * @throws ResourceNotFoundException si el ID no existe
     */
    @Transactional(readOnly = true)
    public ContratoResponse getById(Long id) {
        Contrato contrato = super.findById(id);
        return contratoMapper.toResponse(contrato);
    }

    /**
     * Lista todos los contratos de un cliente ordenados del más reciente al más antiguo.
     *
     * @param clienteId identificador del cliente
     * @return lista de ContratoResponse (sin partidas para optimizar la consulta)
     */
    @Transactional(readOnly = true)
    public List<ContratoResponse> getContratosPorCliente(Integer clienteId) {
        return repository.findByClienteIdOrderByCreadoEnDesc(clienteId)
                .stream()
                .map(contratoMapper::toResponse)
                .toList();
    }

    /**
     * Lista todos los contratos con estatus VENCIDO.
     *
     * @return lista de ContratoResponse vencidos ordenados por fecha de vencimiento
     */
    @Transactional(readOnly = true)
    public List<ContratoResponse> getContratosVencidos() {
        return repository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VENCIDO)
                .stream()
                .map(contratoMapper::toResponse)
                .toList();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Registra el movimiento EMP (empeño, periodo 0) de un contrato recién creado. El monto es el
     * préstamo entregado. Guarda un snapshot "pre-contrato" en los campos {@code *Anterior}
     * (saldo 0, estatus CANCELADO, 0 refrendos) para que la cancelación del día (C-06) deje al
     * contrato en el estado anterior al empeño reutilizando la lógica genérica de reversión.
     */
    private MovimientoContrato registrarMovimientoEmpeno(Contrato contrato, Turno turno, Usuario usuario) {
        MovimientoContrato emp = new MovimientoContrato();
        emp.setContrato(contrato);
        emp.setTurno(turno);
        emp.setUsuario(usuario);
        emp.setTipo(TipoMovimiento.EMP);
        emp.setMonto(contrato.getMontoPrestamo());
        emp.setInteres(BigDecimal.ZERO);
        emp.setPeriodosNormales(0);
        // Snapshot "pre-contrato": lo que el revert del EMP debe restaurar al cancelarlo.
        emp.setSaldoAnterior(BigDecimal.ZERO);
        emp.setEstatusAnterior(EstatusContrato.CANCELADO);
        emp.setNumRefrendosAnterior(0);
        emp.setSaldoNuevo(contrato.getSaldoCapital());
        emp.setFechaContratoNueva(contrato.getFechaContrato());
        emp.setFechaVencNueva(contrato.getFechaVencimiento());
        emp.setEstatusNuevo(contrato.getEstatus());
        emp.setFecha(contrato.getFechaApertura());
        emp.setObservaciones("Empeño");
        movimientoContratoRepository.save(emp);
        return emp;
    }

    /**
     * Copia al contrato los 7 parametros de calculo vigentes (PlazoParametro de la primera partida +
     * IVA) para que la reimpresion futura del PDF y el cobro en caja no cambien de monto si la
     * configuracion vigente se modifica despues (requisito PROFECO, changeset 026). Si la primera
     * partida no tiene PlazoParametro configurado, se dejan en null los 6 campos de plazo (fallback
     * a config vigente en el motor) pero SI se persiste el snapshot del IVA vigente.
     */
    private void aplicarSnapshotCalculo(Contrato contrato, List<PartidaContrato> partidas,
                                         Long plazoId, Integer sucursalId) {
        PlazoParametro representativo = null;
        if (!partidas.isEmpty() && partidas.get(0).getTipoPrenda() != null) {
            representativo = plazoParametroRepository
                    .findByPlazoIdAndTipoPrendaIdAndSucursalId(
                            plazoId, partidas.get(0).getTipoPrenda().getId(), sucursalId)
                    .orElse(null);
        }
        if (representativo != null) {
            contrato.setSnapPorcSancionSemanal(representativo.getPorcSancionSemanal());
            contrato.setSnapDiasGraciaSancion(representativo.getDiasGraciaSinInteres());
            contrato.setSnapAplicarSancionPeriodo(representativo.getAplicarSancionPorPeriodo());
            contrato.setSnapPorcInteres(representativo.getPorcInteres());
            contrato.setSnapPorcAlmacen(representativo.getPorcAlmacen());
            contrato.setSnapPorcGastosAdmin(representativo.getPorcGastosAdmin());
        }
        contrato.setSnapIvaPorcentaje(parametrosSistemaCache.getIvaPorcentaje());
    }

    private PartidaContrato buildPartida(PartidaContratoRequest pr, int numPartida,
                                         Plazo plazo, Integer sucursalId) {
        TipoPrenda tipoPrenda = tipoPrendaRepository.findById(pr.getIdTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TipoPrenda no encontrado: " + pr.getIdTipoPrenda()));

        // D-09: la descripción específica es la defensa antifraude; se valida antes de calcular.
        validarDescripcionEspecifica(pr, numPartida, tipoPrenda);

        // Parámetros del plazo/tipo de prenda/sucursal (puede no existir → reglas por defecto)
        PlazoParametro parametro = plazoParametroRepository
                .findByPlazoIdAndTipoPrendaIdAndSucursalId(plazo.getId(), tipoPrenda.getId(), sucursalId)
                .orElse(null);

        BigDecimal avaluoReal = calcularAvaluoRealServidor(pr, tipoPrenda, plazo.getId(), sucursalId, parametro);

        // Préstamo máximo autorizado para esta partida, a partir de valores YA recalculados por el servidor.
        // ALHAJA: la base es el préstamo de la tabla (no el avalúo real, que es el valor del oro).
        // PLATA: el precio por gramo YA es el precio de préstamo (COCAE: "Calcular sobre Préstamo"),
        // así que el techo es peso × precio (= avaluoReal), SIN aplicar % Préstamo s/Avalúo (ese
        // recorte no aplica a plata; el precio por gramo ya incorpora el préstamo). Alinea con COCAE.
        BigDecimal basePrestamo = calcularBasePrestamoServidor(pr, avaluoReal, tipoPrenda, plazo.getId(), sucursalId);
        BigDecimal prestamoMaximo = calcularPrestamoMaximoServidor(basePrestamo, tipoPrenda, parametro);

        // El préstamo solicitado NUNCA puede superar el máximo (solo ajuste hacia abajo)
        if (pr.getMontoPrestamo() == null || pr.getMontoPrestamo().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Partida " + numPartida + ": el monto de préstamo debe ser mayor que cero");
        }
        // D-06/D-07 Invariante 1: préstamo ≤ base × % préstamo sobre avalúo del plazo (base = préstamo
        // de tabla en ALHAJA, avalúo real en el resto). El mensaje incluye todos los datos del cálculo
        // para que el usuario sepa de dónde salió el máximo (D-07: "el próximo error se entiende solo").
        // Si el plazo tiene porcPrestamoSAvaluo=0 el motor interpreta "sin tope extra" y el porcentaje
        // efectivo es 100%.
        if (pr.getMontoPrestamo().compareTo(prestamoMaximo) > 0) {
            BigDecimal porcEfectivo = parametro != null
                    && parametro.getPorcPrestamoSAvaluo() != null
                    && parametro.getPorcPrestamoSAvaluo().compareTo(BigDecimal.ZERO) > 0
                    ? parametro.getPorcPrestamoSAvaluo() : new BigDecimal("100");
            throw new BadRequestException(String.format(
                    "Partida %d (%s): préstamo $%s supera el máximo autorizado $%s "
                    + "(= %s $%s × %s%% del plazo \"%s\"). "
                    + "Si cambió el plazo, recalcula las partidas antes de generar el contrato.",
                    numPartida, tipoPrenda.getTipo(),
                    pr.getMontoPrestamo(), prestamoMaximo,
                    esAlhaja(tipoPrenda) ? "préstamo de tabla" : "avalúo real", basePrestamo,
                    porcEfectivo.stripTrailingZeros().toPlainString(),
                    plazo.getNombre() != null ? plazo.getNombre() : ("id=" + plazo.getId())));
        }
        // Importe mínimo de préstamo configurado
        if (parametro != null && parametro.getImporteMinPrestamo() != null
                && parametro.getImporteMinPrestamo().compareTo(BigDecimal.ZERO) > 0
                && pr.getMontoPrestamo().compareTo(parametro.getImporteMinPrestamo()) < 0) {
            throw new BadRequestException(String.format(
                    "Partida %d: el préstamo (%s) es menor al mínimo permitido (%s)",
                    numPartida, pr.getMontoPrestamo(), parametro.getImporteMinPrestamo()));
        }

        // D-06 Invariante 2: avalúoContrato = préstamo × (1 + incrementoSobreAvaluo). Lo fija el
        // servidor; si el cliente envió un avaluoContrato distinto (frontend con valores del plazo
        // anterior) se rechaza con el valor esperado para forzar un recálculo.
        BigDecimal avaluoContrato = calcularAvaluoContratoServidor(pr.getMontoPrestamo(), parametro);
        if (pr.getAvaluoContrato() != null
                && pr.getAvaluoContrato().compareTo(BigDecimal.ZERO) > 0
                && pr.getAvaluoContrato().compareTo(avaluoContrato) != 0) {
            throw new BadRequestException(String.format(
                    "Partida %d (%s): el avalúo de contrato enviado (%s) no coincide con el esperado "
                    + "para este plazo (%s = préstamo %s × factor). Recalcula las partidas antes de generar el contrato.",
                    numPartida, tipoPrenda.getTipo(), pr.getAvaluoContrato(), avaluoContrato,
                    pr.getMontoPrestamo()));
        }

        // D-06 Invariante 3: avalúoContrato ≤ avalúoReal. Si el %Prestamo s/Avaluo combinado con el
        // incremento sobre avalúo produce un contrato con avalúo superior al real de la prenda,
        // rechazar: Jorge, 00:17:57 — "el avalúo real tiene que ser al menos igual al avalúo de contrato".
        // No aplica a PLATA: su "avalúo real" es peso × precio de PRÉSTAMO por gramo (el sistema no
        // tiene el valor real de la plata), así que cualquier incremento > 0 la violaría siempre.
        if (!esPlata(tipoPrenda) && avaluoContrato.compareTo(avaluoReal) > 0) {
            throw new BadRequestException(String.format(
                    "Partida %d (%s): el avalúo de contrato (%s) supera el avalúo real (%s). "
                    + "Revisa el %% de préstamo sobre avalúo y el incremento sobre avalúo del plazo.",
                    numPartida, tipoPrenda.getTipo(), avaluoContrato, avaluoReal));
        }

        PartidaContrato partida = new PartidaContrato();
        partida.setNumPartida(numPartida);
        partida.setTipoPrenda(tipoPrenda);
        partida.setDescripcion(pr.getDescripcion().trim());
        partida.setClavePrenda(pr.getClavePrenda());
        // cantidad es informativa: los pesos ya vienen como total del lote, así que NO se
        // multiplica peso × cantidad en ningún punto del cálculo.
        partida.setCantidad(pr.getCantidad() != null ? pr.getCantidad() : 1);
        partida.setPesoNeto(pr.getPesoNeto());
        partida.setPesoTotal(resolverPesoTotal(pr));
        partida.setKilataje(pr.getKilataje());
        partida.setLey(pr.getLey());
        partida.setHechura(pr.getHechura());
        // Para PLATAS el precio por gramo también lo fija el servidor (mismo espíritu que ignorar
        // pr.getAvaluoReal()). resolverPrecioGramoLey ya validó la ley al calcular avaluoReal arriba.
        partida.setPrecioXGramo(esPlata(tipoPrenda)
                ? resolverPrecioGramoLey(pr.getLey(), parametro)
                : pr.getPrecioXGramo());
        partida.setAvaluoReal(avaluoReal);   // valor calculado por el servidor, NO pr.getAvaluoReal()
        partida.setAvaluoContrato(avaluoContrato);
        partida.setMontoPrestamo(pr.getMontoPrestamo());
        partida.setSubtipo(pr.getSubtipo());
        partida.setMarca(pr.getMarca());
        partida.setModelo(pr.getModelo());
        partida.setSerieImei(pr.getSerieImei());
        partida.setEstadoFisico(pr.getEstadoFisico());

        if (pr.getIdValorPrenda() != null) {
            CatValorPrenda valorPrenda = catValorPrendaRepository.findById(pr.getIdValorPrenda())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ValorPrenda no encontrado: " + pr.getIdValorPrenda()));
            if (valorPrenda.getSubtipoPrenda() == null
                    || valorPrenda.getSubtipoPrenda().getTipoPrenda() == null
                    || !tipoPrenda.getId().equals(
                            valorPrenda.getSubtipoPrenda().getTipoPrenda().getId())) {
                throw new BadRequestException(
                        "El valor de catálogo seleccionado no pertenece al tipo de prenda de la partida");
            }
            partida.setValorPrenda(valorPrenda);
            // D-09: clave y descripción general salen del catálogo (no del cliente) y se copian a
            // la partida para que el contrato firmado no cambie si después se edita el catálogo.
            partida.setClavePrenda(valorPrenda.getClave());
            partida.setDescripcionGeneral(valorPrenda.getDescripcion());
        }

        return partida;
    }

    /**
     * Valida la descripción específica de la pieza (D-09). Con descripciones genéricas
     * ("celular", "bocina") el personal puede cambiar un equipo por otro de menor valor sin
     * que auditoría lo note, por eso es obligatoria y con un mínimo de detalle.
     *
     * @param pr         partida solicitada
     * @param numPartida número de partida para el mensaje
     * @param tipoPrenda tipo de prenda ya resuelto
     * @throws BadRequestException si falta la descripción, es muy corta, o en VARIOS falta
     *                             marca, modelo o serie/IMEI
     */
    private void validarDescripcionEspecifica(PartidaContratoRequest pr, int numPartida, TipoPrenda tipoPrenda) {
        String descripcion = pr.getDescripcion() != null ? pr.getDescripcion().trim() : "";
        if (descripcion.length() < Constantes.MIN_CARACTERES_DESCRIPCION_ESPECIFICA) {
            throw new BadRequestException(String.format(
                    "Partida %d: la descripción específica de la prenda es obligatoria y debe tener al menos "
                    + "%d caracteres (ej. \"esclava con mariposa, broche roto\")",
                    numPartida, Constantes.MIN_CARACTERES_DESCRIPCION_ESPECIFICA));
        }
        // TODO Q4: confirmar con Jorge si marca/modelo/serie aplican a todo VARIOS o solo a electrónicos.
        if (esVarios(tipoPrenda)) {
            List<String> faltantes = new ArrayList<>();
            if (pr.getMarca() == null || pr.getMarca().isBlank()) faltantes.add("marca");
            if (pr.getModelo() == null || pr.getModelo().isBlank()) faltantes.add("modelo");
            if (pr.getSerieImei() == null || pr.getSerieImei().isBlank()) faltantes.add("serie/IMEI");
            if (!faltantes.isEmpty()) {
                throw new BadRequestException(String.format(
                        "Partida %d (VARIOS): falta capturar %s", numPartida, String.join(", ", faltantes)));
            }
        }
    }

    /**
     * Resuelve el peso total (físico) que se persiste en la partida.
     *
     * Una pieza de oro o plata puede traer material que no es metal precioso: piedras,
     * plástico o soldadura. El peso neto es solo el metal (y es el que se cobra); el peso
     * total es lo que pesa físicamente la pieza completa, y es informativo para el cliente.
     *
     * Reglas (confirmadas con Jorge, 2026-09-08):
     *   - Varios y Autos/Motos no se valúan por gramo: sin peso neto no hay peso total.
     *   - Si el usuario no captura el total, se asume que la pieza es 100% metal.
     *   - El total nunca puede ser menor que el neto: sería físicamente imposible.
     *
     * @param pr datos de la partida solicitada
     * @return peso total a persistir, o null si la partida no se valúa por gramo
     * @throws BadRequestException si el peso total capturado es menor que el peso neto
     */
    private BigDecimal resolverPesoTotal(PartidaContratoRequest pr) {
        if (pr.getPesoNeto() == null) {
            return null;
        }
        if (pr.getPesoTotal() == null) {
            return pr.getPesoNeto();
        }
        if (pr.getPesoTotal().compareTo(pr.getPesoNeto()) < 0) {
            throw new BadRequestException(String.format(
                    "El peso total (%s g) no puede ser menor que el peso neto (%s g)",
                    pr.getPesoTotal(), pr.getPesoNeto()));
        }
        return pr.getPesoTotal();
    }

    /**
     * Recalcula el avalúo real de una partida ALHAJA, ignorando el avaluoReal que envía el
     * cliente (D-07, Pitfall 1 de PITFALLS.md). El avalúo real es el VALOR de la prenda
     * (oro del kilataje × factor de hechura × peso neto), no el préstamo de la tabla: la
     * columna PRÉSTAMO ya trae aplicado el %Préstamo de la tabla de oro y el aumento del plazo.
     *
     * @param pr         datos de la partida solicitada
     * @param plazoId    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return avalúo real calculado por el servidor, escala 2 (HALF_UP)
     * @throws BadRequestException si el kilataje es 24K, no soportado, o falta peso neto/hechura
     * @throws ResourceNotFoundException si no existe tabla de precios o %Prestamo para la celda
     */
    private BigDecimal calcularAvaluoRealAlhaja(PartidaContratoRequest pr, Long plazoId, Integer sucursalId) {
        PlazoHechuraAlhaja fila = buscarFilaAlhaja(pr, plazoId, sucursalId);
        return plazoService.calcularPrecioAvaluoGramo(fila)
                .multiply(pr.getPesoNeto())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Préstamo que da la tabla del plazo para una partida ALHAJA: precio de préstamo de la
     * celda kilataje/hechura × peso neto. Es la base del préstamo máximo de la partida.
     *
     * @param pr         datos de la partida solicitada
     * @param plazoId    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return préstamo de tabla, escala 2 (HALF_UP)
     */
    private BigDecimal calcularPrestamoTablaAlhaja(PartidaContratoRequest pr, Long plazoId, Integer sucursalId) {
        return buscarFilaAlhaja(pr, plazoId, sucursalId).getPrecioPrestamo()
                .multiply(pr.getPesoNeto())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valida kilataje, hechura y peso de una partida ALHAJA y devuelve su celda de la tabla
     * de precios del plazo (PlazoHechuraAlhaja).
     *
     * @param pr         datos de la partida solicitada
     * @param plazoId    identificador del plazo (se convierte a Integer explícitamente —
     *                   PlazoHechuraAlhajaId usa Integer, Plazo.id es Long)
     * @param sucursalId identificador de la sucursal
     * @return celda de la tabla de precios
     * @throws BadRequestException si el kilataje es 24K, no soportado, o falta peso neto/hechura
     * @throws ResourceNotFoundException si no existe tabla de precios para la celda
     */
    private PlazoHechuraAlhaja buscarFilaAlhaja(PartidaContratoRequest pr, Long plazoId, Integer sucursalId) {
        Integer kilataje = pr.getKilataje();
        if (kilataje == null) {
            throw new BadRequestException("Kilataje es requerido para partidas de tipo ALHAJA");
        }
        if (kilataje == 24) {
            throw new BadRequestException("Oro de 24K no es prendable");
        }
        if (!KILATAJES_COCAE.contains(kilataje)) {
            throw new BadRequestException("Kilataje no soportado: " + kilataje);
        }
        if (pr.getHechura() == null || pr.getHechura().isBlank()) {
            throw new BadRequestException("Hechura es requerida para partidas de tipo ALHAJA");
        }
        if (pr.getPesoNeto() == null || pr.getPesoNeto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El peso neto debe ser mayor que cero para partidas ALHAJA");
        }
        String hechura = pr.getHechura();
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(Math.toIntExact(plazoId), sucursalId, kilataje, hechura);
        return plazoHechuraAlhajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay precio configurado para plazo=" + plazoId + ", kilataje=" + kilataje
                        + ", hechura=" + hechura));
    }

    /**
     * Determina si un tipo de prenda corresponde a ALHAJA (oro), comparando por texto
     * en vez del id hardcodeado para mayor robustez.
     *
     * @param tipoPrenda tipo de prenda a evaluar
     * @return true si tipoPrenda.getTipo() es "ALHAJA" (case-insensitive)
     */
    private boolean esAlhaja(TipoPrenda tipoPrenda) {
        return tipoPrenda != null && "ALHAJA".equalsIgnoreCase(tipoPrenda.getTipo());
    }

    /**
     * Determina si un tipo de prenda corresponde a PLATA, comparando por texto
     * en vez del id hardcodeado, igual que esAlhaja().
     * El valor real sembrado en tipo_prenda es "PLATAS" en PLURAL (id=4, D-04).
     *
     * @param tipoPrenda tipo de prenda a evaluar
     * @return true si tipoPrenda.getTipo() es "PLATAS" (case-insensitive)
     */
    private boolean esPlata(TipoPrenda tipoPrenda) {
        return tipoPrenda != null && "PLATAS".equalsIgnoreCase(tipoPrenda.getTipo());
    }

    /** Determina si un tipo de prenda corresponde a VARIOS (electrónicos y artículos), por texto. */
    private boolean esVarios(TipoPrenda tipoPrenda) {
        return tipoPrenda != null && "VARIOS".equalsIgnoreCase(tipoPrenda.getTipo());
    }

    /**
     * Resuelve el precio por gramo configurado en el plazo para la ley solicitada.
     * Fuente de verdad server-side para plata: nunca se usa pr.getPrecioXGramo().
     *
     * @param ley       ley de la pieza (solo se soportan 925 y 720, D-01/D-03)
     * @param parametro parámetros del plazo/tipoPrenda/sucursal ya resueltos en buildPartida
     * @return precio por gramo configurado para esa ley (siempre > 0)
     * @throws BadRequestException si falta parametro, falta la ley, la ley no es 925/720,
     *                             o el precio de esa ley no está configurado (null o <= 0)
     */
    private BigDecimal resolverPrecioGramoLey(BigDecimal ley, PlazoParametro parametro) {
        if (parametro == null) {
            throw new BadRequestException(MSG_PLATA_SIN_CONFIG);
        }
        if (ley == null) {
            throw new BadRequestException("Ley es requerida para partidas de tipo PLATA");
        }
        BigDecimal precioGramo;
        if (ley.compareTo(LEY_925) == 0) {
            precioGramo = parametro.getLey925();
        } else if (ley.compareTo(LEY_720) == 0) {
            // Columna ley_725 (nombre legacy) — guarda el precio por gramo de la ley baja (720)
            precioGramo = parametro.getLey725();
        } else {
            throw new BadRequestException("Ley no soportada: " + ley);
        }
        if (precioGramo == null || precioGramo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Precio por gramo no configurado para ley " + ley);
        }
        return precioGramo;
    }

    /**
     * Recalcula el avalúo real de una partida PLATA a partir del precio por gramo
     * configurado para su ley (925 o 720) en el PlazoParametro ya resuelto para este
     * plazo/tipoPrenda/sucursal, ignorando el avaluoReal que envía el cliente.
     * Cierra la brecha de confianza servidor/cliente para plata (PLATA-02), mismo
     * espíritu que calcularAvaluoRealAlhaja para oro.
     *
     * Fórmula (D-01, confirmada con Jorge 2026-08-06):
     *     avaluo = pesoNeto x precioGramoDeEsaLey
     * No hay ninguna división por 1000 ni derivación desde precio de onza: ley925/ley725
     * ya son precios finales por gramo capturados manualmente en Plazos y Periodos.
     *
     * @param pr        datos de la partida solicitada (requiere ley y pesoNeto)
     * @param parametro parámetros del plazo ya resueltos en buildPartida (puede ser null)
     * @return avalúo real calculado por el servidor, escala 2 (HALF_UP)
     * @throws BadRequestException si falta parametro, peso <= 0, falta la ley,
     *                             la ley no es 925/720, o el precio de esa ley no está configurado
     */
    private BigDecimal calcularAvaluoRealPlata(PartidaContratoRequest pr, PlazoParametro parametro) {
        if (parametro == null) {
            throw new BadRequestException(MSG_PLATA_SIN_CONFIG);
        }
        if (pr.getPesoNeto() == null || pr.getPesoNeto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El peso neto debe ser mayor que cero para partidas PLATA");
        }
        BigDecimal precioGramo = resolverPrecioGramoLey(pr.getLey(), parametro);
        return precioGramo
                .multiply(pr.getPesoNeto())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * ¿Al tipo de prenda le aplica el redondeo del total a múltiplo de $5 (C-08 / RN-30)?
     * Único lugar donde vive la regla por tipo: cuando el negocio confirme si extender
     * la regla a plata/relojes/varios (G-04), basta con tocar este método.
     *
     * TODO G-04: hoy aplica solo a alhajas; pendiente de validar con Jorge.
     */
    private boolean aplicaRedondeoMultiploDe5(TipoPrenda tipoPrenda) {
        return esAlhaja(tipoPrenda);
    }

    /** floor(valor / 5) × 5 con escala 2. Nunca redondea hacia arriba (ver C-08). */
    private BigDecimal redondearAMultiploDe5(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valor.divide(MULTIPLO_REDONDEO_PRESTAMO, 0, RoundingMode.FLOOR)
                .multiply(MULTIPLO_REDONDEO_PRESTAMO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valida la regla C-08/RN-30: el total prestado en partidas sujetas al redondeo (hoy solo
     * alhajas) debe ser múltiplo de $5. El frontend ajusta la última partida; si el request
     * llega sin el ajuste, rechazamos con un mensaje que explica cómo corregirlo.
     */
    private void validarRedondeoMultiploDe5(List<PartidaContrato> partidas) {
        BigDecimal totalRedondeable = partidas.stream()
                .filter(p -> aplicaRedondeoMultiploDe5(p.getTipoPrenda()))
                .map(PartidaContrato::getMontoPrestamo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRedondeable.signum() <= 0) {
            return;
        }
        BigDecimal esperado = redondearAMultiploDe5(totalRedondeable);
        if (totalRedondeable.compareTo(esperado) != 0) {
            throw new BadRequestException(String.format(
                    "El préstamo total de alhajas (%s) debe ser múltiplo de $5. "
                    + "Esperado: %s. Ajuste la última partida de alhaja para descontar la diferencia.",
                    totalRedondeable.setScale(2, RoundingMode.HALF_UP), esperado));
        }
    }

    /**
     * Calcula el préstamo máximo autorizado para una partida según el tipo de prenda:
     *   - El tope base es el avalúo YA recalculado por el servidor (avaluoReal).
     *   - Si el parámetro define un % de préstamo sobre avalúo (porcPrestamoSAvaluo > 0),
     *     el máximo es avaluoReal * porcPrestamoSAvaluo / 100.
     *   - Para prendas de libre avalúo (varios/electrónicos/autos) sin % configurado,
     *     el tope es el propio avalúo real (no se aplica la regla del oro).
     *
     * @param avaluoReal avalúo real ya calculado (server-side para ALHAJA, del cliente para el resto)
     * @param parametro  parámetros del plazo (puede ser null)
     * @return préstamo máximo autorizado, escala 2 (HALF_UP)
     */
    private BigDecimal calcularPrestamoMaximo(BigDecimal avaluoReal, PlazoParametro parametro) {
        BigDecimal avaluo = avaluoReal != null ? avaluoReal : BigDecimal.ZERO;
        if (parametro != null
                && parametro.getPorcPrestamoSAvaluo() != null
                && parametro.getPorcPrestamoSAvaluo().compareTo(BigDecimal.ZERO) > 0) {
            return avaluo
                    .multiply(parametro.getPorcPrestamoSAvaluo())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return avaluo.setScale(2, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // D-06: Recálculo server-side al cambiar de plazo
    // =========================================================================

    /**
     * Avalúo real que el servidor fija para una partida: ALHAJA sale de la tabla
     * PlazoHechuraAlhaja; PLATAS sale del precio por gramo configurado en PlazoParametro
     * (ley 925 o 720); el resto conserva el valor libre que capturó el valuador.
     */
    private BigDecimal calcularAvaluoRealServidor(PartidaContratoRequest pr, TipoPrenda tipoPrenda,
                                                   Long plazoId, Integer sucursalId,
                                                   PlazoParametro parametro) {
        if (esAlhaja(tipoPrenda)) {
            return calcularAvaluoRealAlhaja(pr, plazoId, sucursalId);
        }
        if (esPlata(tipoPrenda)) {
            return calcularAvaluoRealPlata(pr, parametro);
        }
        return pr.getAvaluoReal() != null ? pr.getAvaluoReal() : BigDecimal.ZERO;
    }

    /**
     * Base sobre la que se aplica el % de préstamo sobre avalúo del plazo. ALHAJA: el préstamo
     * de la tabla (precio de préstamo × peso), porque el avalúo real es el valor del oro y la
     * tabla ya descuenta el %Préstamo. Resto: el avalúo real.
     */
    private BigDecimal calcularBasePrestamoServidor(PartidaContratoRequest pr, BigDecimal avaluoReal,
                                                     TipoPrenda tipoPrenda, Long plazoId, Integer sucursalId) {
        return esAlhaja(tipoPrenda) ? calcularPrestamoTablaAlhaja(pr, plazoId, sucursalId) : avaluoReal;
    }

    /**
     * Préstamo máximo autorizado según tipo de prenda. PLATA: techo = avalúo real
     * (el precio por gramo YA es el precio de préstamo COCAE). Resto: base × % de préstamo
     * sobre avalúo del plazo (ver calcularBasePrestamoServidor).
     */
    private BigDecimal calcularPrestamoMaximoServidor(BigDecimal basePrestamo, TipoPrenda tipoPrenda,
                                                       PlazoParametro parametro) {
        return esPlata(tipoPrenda)
                ? (basePrestamo != null ? basePrestamo : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : calcularPrestamoMaximo(basePrestamo, parametro);
    }

    /**
     * Avalúo de contrato según el plazo. Delega en PlazoService cuando hay parámetro;
     * si no hay parámetro (varios/electrónicos sin configuración), el avalúo de contrato
     * es igual al préstamo (invariante 2 degenera a identidad).
     */
    private BigDecimal calcularAvaluoContratoServidor(BigDecimal prestamo, PlazoParametro parametro) {
        if (parametro != null) {
            return plazoService.calcularAvaluoContrato(prestamo, parametro);
        }
        return prestamo != null ? prestamo.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Precio por gramo que el servidor fija en la partida. Para PLATAS lo lee del
     * PlazoParametro (ley 925 o 720); para el resto conserva el valor del cliente.
     */
    private BigDecimal calcularPrecioXGramoServidor(PartidaContratoRequest pr, TipoPrenda tipoPrenda,
                                                     PlazoParametro parametro) {
        if (esPlata(tipoPrenda)) {
            return resolverPrecioGramoLey(pr.getLey(), parametro);
        }
        return pr.getPrecioXGramo();
    }

    /**
     * Recalcula cada partida con los parámetros de un plazo nuevo (D-06). El cliente invoca
     * este endpoint cuando el valuador cambia de plazo en el avalúo, de modo que ningún valor
     * del plazo anterior quede "pegado" a la partida. El préstamo que supera el máximo nuevo
     * se recorta hacia abajo (nunca hacia arriba); el total de alhajas se baja al múltiplo de
     * $5 descontando la diferencia de la última partida de alhaja (RN-30).
     *
     * @param request idPlazo y las partidas capturadas en el avalúo
     * @return partidas recalculadas con precio por gramo, avalúo real, préstamo máximo,
     *         préstamo final (clamp + redondeo) y avalúo de contrato — sin persistir.
     */
    @Transactional(readOnly = true)
    public List<PartidaRecalculadaResponse> recalcularPartidas(RecalcularPartidasRequest request) {
        Plazo plazo = plazoRepository.findById(request.getIdPlazo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plazo no encontrado: " + request.getIdPlazo()));
        Integer sucursalId = 1; // TODO: derivar de la sucursal del usuario/turno (mismo convenio que crearContrato)

        List<PartidaRecalculadaResponse> resultado = new ArrayList<>();
        List<BigDecimal> prestamosAlhaja = new ArrayList<>();
        List<Integer> idxAlhaja = new ArrayList<>();

        for (int i = 0; i < request.getPartidas().size(); i++) {
            PartidaContratoRequest pr = request.getPartidas().get(i);
            TipoPrenda tipoPrenda = tipoPrendaRepository.findById(pr.getIdTipoPrenda())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "TipoPrenda no encontrado: " + pr.getIdTipoPrenda()));
            PlazoParametro parametro = plazoParametroRepository
                    .findByPlazoIdAndTipoPrendaIdAndSucursalId(plazo.getId(), tipoPrenda.getId(), sucursalId)
                    .orElse(null);

            BigDecimal avaluoReal = calcularAvaluoRealServidor(pr, tipoPrenda, plazo.getId(), sucursalId, parametro);
            BigDecimal basePrestamo = calcularBasePrestamoServidor(pr, avaluoReal, tipoPrenda, plazo.getId(), sucursalId);
            BigDecimal prestamoMaximo = calcularPrestamoMaximoServidor(basePrestamo, tipoPrenda, parametro);
            BigDecimal precioXGramo = calcularPrecioXGramoServidor(pr, tipoPrenda, parametro);

            // El préstamo del plazo anterior puede superar el máximo del plazo nuevo. Clamp hacia
            // abajo (nunca hacia arriba). El importe mínimo NO se fuerza aquí: dejar que el
            // valuador vea el resultado y decida (si quedó bajo el mínimo, crearContrato rechaza).
            BigDecimal prestamoSolicitado = pr.getMontoPrestamo() != null
                    ? pr.getMontoPrestamo() : BigDecimal.ZERO;
            BigDecimal montoPrestamo = prestamoSolicitado.compareTo(prestamoMaximo) > 0
                    ? prestamoMaximo
                    : prestamoSolicitado.setScale(2, RoundingMode.HALF_UP);

            PartidaRecalculadaResponse row = new PartidaRecalculadaResponse();
            row.setNumPartida(i + 1);
            row.setIdTipoPrenda(tipoPrenda.getId());
            row.setPrecioXGramo(precioXGramo);
            row.setAvaluoReal(avaluoReal);
            row.setPrestamoMaximo(prestamoMaximo);
            row.setMontoPrestamo(montoPrestamo);
            // Avalúo de contrato se calcula al final porque el redondeo a $5 puede ajustar el préstamo
            resultado.add(row);

            if (aplicaRedondeoMultiploDe5(tipoPrenda)) {
                idxAlhaja.add(i);
                prestamosAlhaja.add(montoPrestamo);
            }
        }

        // RN-30 (C-08): el total prestado en alhajas se baja al múltiplo de $5. La diferencia se
        // descuenta de la ÚLTIMA partida de alhaja. Si el redondeo deja la última partida por
        // debajo de 0 (edge: diferencia > último préstamo), se absorbe en 0 y no se vuelve a bajar.
        if (!idxAlhaja.isEmpty()) {
            BigDecimal total = prestamosAlhaja.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalRedondeado = redondearAMultiploDe5(total);
            BigDecimal diferencia = total.subtract(totalRedondeado);
            if (diferencia.signum() > 0) {
                int ultimo = idxAlhaja.get(idxAlhaja.size() - 1);
                PartidaRecalculadaResponse row = resultado.get(ultimo);
                BigDecimal ajustado = row.getMontoPrestamo().subtract(diferencia);
                row.setMontoPrestamo(ajustado.signum() < 0
                        ? BigDecimal.ZERO.setScale(2)
                        : ajustado.setScale(2, RoundingMode.HALF_UP));
            }
        }

        // Avalúo de contrato final sobre el préstamo ya ajustado.
        for (int i = 0; i < resultado.size(); i++) {
            PartidaContratoRequest pr = request.getPartidas().get(i);
            PartidaRecalculadaResponse row = resultado.get(i);
            TipoPrenda tipoPrenda = tipoPrendaRepository.findById(pr.getIdTipoPrenda()).orElseThrow();
            PlazoParametro parametro = plazoParametroRepository
                    .findByPlazoIdAndTipoPrendaIdAndSucursalId(plazo.getId(), tipoPrenda.getId(), sucursalId)
                    .orElse(null);
            row.setAvaluoContrato(calcularAvaluoContratoServidor(row.getMontoPrestamo(), parametro));
        }
        return resultado;
    }

    /**
     * Genera la tabla de amortización (vencimientos por periodo) al vuelo, sin
     * persistir las fechas intermedias. Solo se guardan en BD la fecha de apertura
     * y la fecha de vencimiento final.
     *
     * @param contratoId identificador del contrato
     * @return lista de vencimientos calculados (uno por periodo)
     */
    @Transactional(readOnly = true)
    public List<com.ignis.prestamil.response.VencimientoResponse> calcularAmortizacion(Long contratoId) {
        Contrato contrato = super.findById(contratoId);
        Plazo plazo = contrato.getPlazo();
        PlazoParametro parametro = null;
        if (contrato.getPartidas() != null && !contrato.getPartidas().isEmpty()
                && contrato.getPartidas().get(0).getTipoPrenda() != null) {
            parametro = plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(
                    plazo.getId(), contrato.getPartidas().get(0).getTipoPrenda().getId(),
                    contrato.getSucursalId()).orElse(null);
        }
        BigDecimal prestamo = contrato.getMontoPrestamo();

        // Pasada 2: resolver parametros efectivos una vez (snapshot > vigente) y delegar el calculo
        // por fila al motor unico. Para filas NORMALES pasamos la misma fecha como vencimiento y como
        // pago -> atraso=0 y sancion=0 en toda la amortizacion (la sancion solo aplica en filas
        // extemporaneas del PDF y en el cobro real de refrendar). Usamos fechaApertura como fecha segura
        // (nunca null porque la entidad la exige) en vez de fechaVencimiento que si podria serlo en
        // fixtures antiguos.
        ParametrosCalculo pc = calculoContratoService.resolverParametros(contrato, parametro);
        LocalDate fechaSinAtraso = contrato.getFechaApertura().toLocalDate();

        List<com.ignis.prestamil.response.VencimientoResponse> filas = new ArrayList<>();
        LocalDate base = contrato.getFechaApertura().toLocalDate();
        // La amortización usa diasPorPeriodo y numeroPeriodos del plazo (NO constantes hardcodeadas):
        // semanal => 7 x 4, quincenal => 15 x 3, mensual => 30 x 1, etc.
        // TODO Q1 (PLAN-CORRECCIONES-07OCT): confirmar con Jorge si la quincena dura 14 o 15 días.
        // Hoy la BD guarda 15 (coincide con COCAE); el motor respeta el valor configurado.
        for (int n = 1; n <= plazo.getNumeroPeriodos(); n++) {
            DesgloseCobro d = calculoContratoService.calcularCobroPeriodo(
                    prestamo, pc, fechaSinAtraso, fechaSinAtraso, n);
            BigDecimal totalInt = d.interesTotal();
            BigDecimal desempeno = prestamo.add(totalInt).add(d.iva()).setScale(2, RoundingMode.HALF_UP);

            com.ignis.prestamil.response.VencimientoResponse v =
                    new com.ignis.prestamil.response.VencimientoResponse();
            v.setPeriodo(n);
            v.setFecha(base.plusDays((long) plazo.getDiasPorPeriodo() * n));
            v.setInteres(d.interes());
            v.setAlmacen(d.almacen());
            v.setTotalInteres(totalInt);
            v.setIva(d.iva());
            v.setDesempeno(desempeno);
            v.setTotal(desempeno);
            filas.add(v);
        }
        return filas;
    }
}
