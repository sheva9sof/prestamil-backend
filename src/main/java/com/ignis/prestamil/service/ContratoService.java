package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.*;
import com.ignis.prestamil.repository.*;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.PartidaContratoRequest;
import com.ignis.prestamil.response.ContratoResponse;
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

    private static final List<Integer> KILATAJES_COCAE = List.of(6, 8, 10, 12, 14, 18, 21, 24);
    private static final BigDecimal LEY_925 = new BigDecimal("925");
    // Ley de plata baja: COCAE la maneja como 720 (fineness estándar 0.720). La columna de precio
    // sigue llamándose ley_725 por compatibilidad; almacena el precio por gramo de esta ley.
    private static final BigDecimal LEY_720 = new BigDecimal("720");
    // IVA sobre el interés total. COCAE aplica 16% y lo TRUNCA a 2 decimales (verificado con capturas).
    private static final BigDecimal IVA_PORCENTAJE = new BigDecimal("16");
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
                           PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository) {
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
            PartidaContrato partida = buildPartida(pr, i + 1, plazo.getId(), sucursalId);
            totalPrestamo = totalPrestamo.add(partida.getMontoPrestamo());
            totalAvaluo = totalAvaluo.add(partida.getAvaluoContrato());
            partidas.add(partida);
        }

        // 7. Construir contrato
        Contrato contrato = new Contrato();
        contrato.setCliente(cliente);
        contrato.setTurno(turno);
        contrato.setSucursalId(1);
        contrato.setPlazo(plazo);
        contrato.setUsuario(usuario);
        contrato.setFechaApertura(fechaApertura);
        contrato.setFechaVencimiento(fechaVencimiento);
        contrato.setMontoPrestamo(totalPrestamo);
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

        // 9. Primera persistencia para obtener el ID generado
        Contrato guardado = repository.save(contrato);

        // 10. Asignar folio basado en el ID y persistir de nuevo
        guardado.setFolio(String.format("CTR-%06d", guardado.getId()));
        guardado = repository.save(guardado);

        log.info("Contrato creado: {} | cliente={} | monto={}", guardado.getFolio(),
                cliente.getId(), totalPrestamo);

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

    private PartidaContrato buildPartida(PartidaContratoRequest pr, int numPartida,
                                         Long plazoId, Integer sucursalId) {
        TipoPrenda tipoPrenda = tipoPrendaRepository.findById(pr.getIdTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TipoPrenda no encontrado: " + pr.getIdTipoPrenda()));

        // Parámetros del plazo/tipo de prenda/sucursal (puede no existir → reglas por defecto)
        PlazoParametro parametro = plazoParametroRepository
                .findByPlazoIdAndTipoPrendaIdAndSucursalId(plazoId, tipoPrenda.getId(), sucursalId)
                .orElse(null);

        // Avalúo real: para ALHAJA lo calcula el servidor a partir de PlazoHechuraAlhaja (D-07 de Phase 4);
        // para PLATAS lo calcula el servidor a partir de ley925/ley725 del PlazoParametro (D-05/D-06 de Phase 6).
        // Para el resto (Varios/electrónicos, avalúo libre del valuador) se conserva el valor del cliente.
        BigDecimal avaluoReal = esAlhaja(tipoPrenda)
                ? calcularAvaluoRealAlhaja(pr, plazoId, sucursalId)
                : esPlata(tipoPrenda)
                    ? calcularAvaluoRealPlata(pr, parametro)
                    : (pr.getAvaluoReal() != null ? pr.getAvaluoReal() : BigDecimal.ZERO);

        // Préstamo máximo autorizado para esta partida, a partir del avalúo YA recalculado por el servidor.
        // PLATA: el precio por gramo YA es el precio de préstamo (COCAE: "Calcular sobre Préstamo"),
        // así que el techo es peso × precio (= avaluoReal), SIN aplicar % Préstamo s/Avalúo (ese
        // recorte no aplica a plata; el precio por gramo ya incorpora el préstamo). Alinea con COCAE.
        BigDecimal prestamoMaximo = esPlata(tipoPrenda)
                ? avaluoReal
                : calcularPrestamoMaximo(avaluoReal, parametro);

        // El préstamo solicitado NUNCA puede superar el máximo (solo ajuste hacia abajo)
        if (pr.getMontoPrestamo() == null || pr.getMontoPrestamo().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Partida " + numPartida + ": el monto de préstamo debe ser mayor que cero");
        }
        if (pr.getMontoPrestamo().compareTo(prestamoMaximo) > 0) {
            throw new BadRequestException(String.format(
                    "Partida %d: el préstamo (%s) supera el máximo autorizado (%s)",
                    numPartida, pr.getMontoPrestamo(), prestamoMaximo));
        }
        // Importe mínimo de préstamo configurado
        if (parametro != null && parametro.getImporteMinPrestamo() != null
                && parametro.getImporteMinPrestamo().compareTo(BigDecimal.ZERO) > 0
                && pr.getMontoPrestamo().compareTo(parametro.getImporteMinPrestamo()) < 0) {
            throw new BadRequestException(String.format(
                    "Partida %d: el préstamo (%s) es menor al mínimo permitido (%s)",
                    numPartida, pr.getMontoPrestamo(), parametro.getImporteMinPrestamo()));
        }

        // Avalúo del contrato: lo fija el servidor (no se confía en el valor del cliente)
        BigDecimal avaluoContrato = parametro != null
                ? plazoService.calcularAvaluoContrato(pr.getMontoPrestamo(), parametro)
                : pr.getMontoPrestamo();

        PartidaContrato partida = new PartidaContrato();
        partida.setNumPartida(numPartida);
        partida.setTipoPrenda(tipoPrenda);
        partida.setDescripcion(pr.getDescripcion());
        partida.setClavePrenda(pr.getClavePrenda());
        partida.setCantidad(pr.getCantidad() != null ? pr.getCantidad() : 1);
        partida.setPesoGramos(pr.getPesoGramos());
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
            partida.setValorPrenda(valorPrenda);
        }

        return partida;
    }

    /**
     * Recalcula el avalúo real de una partida ALHAJA a partir de la tabla de precios
     * del plazo (PlazoHechuraAlhaja), ignorando el avaluoReal que envía el cliente.
     * Cierra la brecha de confianza servidor/cliente (D-07, Pitfall 1 de PITFALLS.md).
     *
     * @param pr         datos de la partida solicitada
     * @param plazoId    identificador del plazo (se convierte a Integer explícitamente —
     *                   PlazoHechuraAlhajaId usa Integer, Plazo.id es Long)
     * @param sucursalId identificador de la sucursal
     * @return avalúo real calculado por el servidor, escala 2 (HALF_UP)
     * @throws BadRequestException si el kilataje es 24K, no soportado, o falta peso/hechura
     * @throws ResourceNotFoundException si no existe tabla de precios para la celda
     */
    private BigDecimal calcularAvaluoRealAlhaja(PartidaContratoRequest pr, Long plazoId, Integer sucursalId) {
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
        if (pr.getPesoGramos() == null || pr.getPesoGramos().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Peso en gramos debe ser mayor que cero para partidas ALHAJA");
        }
        String hechura = pr.getHechura();
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(Math.toIntExact(plazoId), sucursalId, kilataje, hechura);
        PlazoHechuraAlhaja tabla = plazoHechuraAlhajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay precio configurado para plazo=" + plazoId + ", kilataje=" + kilataje
                        + ", hechura=" + hechura));
        return tabla.getPrecioPrestamo()
                .multiply(pr.getPesoGramos())
                .setScale(2, RoundingMode.HALF_UP);
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
     *     avaluo = pesoGramos x precioGramoDeEsaLey
     * No hay ninguna división por 1000 ni derivación desde precio de onza: ley925/ley725
     * ya son precios finales por gramo capturados manualmente en Plazos y Periodos.
     *
     * @param pr        datos de la partida solicitada (requiere ley y pesoGramos)
     * @param parametro parámetros del plazo ya resueltos en buildPartida (puede ser null)
     * @return avalúo real calculado por el servidor, escala 2 (HALF_UP)
     * @throws BadRequestException si falta parametro, peso <= 0, falta la ley,
     *                             la ley no es 925/720, o el precio de esa ley no está configurado
     */
    private BigDecimal calcularAvaluoRealPlata(PartidaContratoRequest pr, PlazoParametro parametro) {
        if (parametro == null) {
            throw new BadRequestException(MSG_PLATA_SIN_CONFIG);
        }
        if (pr.getPesoGramos() == null || pr.getPesoGramos().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Peso en gramos debe ser mayor que cero para partidas PLATA");
        }
        BigDecimal precioGramo = resolverPrecioGramoLey(pr.getLey(), parametro);
        return precioGramo
                .multiply(pr.getPesoGramos())
                .setScale(2, RoundingMode.HALF_UP);
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
        BigDecimal cien = new BigDecimal("100");
        BigDecimal porcInteres = parametro != null && parametro.getPorcInteres() != null
                ? parametro.getPorcInteres() : BigDecimal.ZERO;
        BigDecimal porcAlmacen = parametro != null && parametro.getPorcAlmacen() != null
                ? parametro.getPorcAlmacen() : BigDecimal.ZERO;
        BigDecimal porcGastos = parametro != null && parametro.getPorcGastosAdmin() != null
                ? parametro.getPorcGastosAdmin() : BigDecimal.ZERO;
        // Total interés = interés + almacén + gastos admin. COCAE lo muestra sumado; el campo
        // porc_interes_total es redundante y puede quedar en 0, así que lo DERIVAMOS de los componentes.
        BigDecimal porcTotal = porcInteres.add(porcAlmacen).add(porcGastos);

        // Montos base por periodo (sin redondear, escala 6)
        BigDecimal interesPer  = prestamo.multiply(porcInteres).divide(cien, 6, RoundingMode.HALF_UP);
        BigDecimal almacenPer  = prestamo.multiply(porcAlmacen).divide(cien, 6, RoundingMode.HALF_UP);
        BigDecimal gastosPer   = prestamo.multiply(porcGastos).divide(cien, 6, RoundingMode.HALF_UP);
        BigDecimal totalIntPer = prestamo.multiply(porcTotal).divide(cien, 6, RoundingMode.HALF_UP);

        List<com.ignis.prestamil.response.VencimientoResponse> filas = new ArrayList<>();
        LocalDate base = contrato.getFechaApertura().toLocalDate();
        for (int n = 1; n <= plazo.getNumeroPeriodos(); n++) {
            BigDecimal factor = new BigDecimal(n);
            BigDecimal interes  = interesPer.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal almacen  = almacenPer.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gastos   = gastosPer.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalInt = totalIntPer.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            // IVA: COCAE lo TRUNCA a 2 decimales (p.ej. 22.05 x 16% = 3.528 -> 3.52)
            BigDecimal iva = totalIntPer.multiply(factor).multiply(IVA_PORCENTAJE)
                    .divide(cien, 2, RoundingMode.DOWN);
            BigDecimal desempeno = prestamo.add(totalInt).add(iva).setScale(2, RoundingMode.HALF_UP);

            com.ignis.prestamil.response.VencimientoResponse v =
                    new com.ignis.prestamil.response.VencimientoResponse();
            v.setPeriodo(n);
            v.setFecha(base.plusDays((long) plazo.getDiasPorPeriodo() * n));
            v.setInteres(interes);
            v.setAlmacen(almacen);
            v.setGastosAdmin(gastos);
            v.setTotalInteres(totalInt);
            v.setIva(iva);
            v.setDesempeno(desempeno);
            v.setTotal(desempeno);
            filas.add(v);
        }
        return filas;
    }
}
