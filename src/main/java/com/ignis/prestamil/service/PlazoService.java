package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.request.PrecioOroRequest;
import com.ignis.prestamil.mapper.PlazoHechuraAlhajaMapper;
import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.mapper.PlazoParametroMapper;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.PlazoParametroId;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.request.PlazoHechuraAlhajaRequest;
import com.ignis.prestamil.request.PlazoParametroRequest;
import com.ignis.prestamil.request.PlazoRequest;
import com.ignis.prestamil.response.PlazoHechuraAlhajaResponse;
import com.ignis.prestamil.response.PlazoParametroResponse;
import com.ignis.prestamil.response.PlazoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@Slf4j
public class PlazoService extends BaseService<Plazo, Long, PlazoRepository> {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final PlazoMapper plazoMapper;
    private final TipoPrendaService tipoPrendaService;
    private final PlazoParametroRepository plazoParametroRepository;
    private final PlazoParametroMapper plazoParametroMapper;
    private final PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;
    private final PlazoHechuraAlhajaMapper plazoHechuraAlhajaMapper;
    private final com.ignis.prestamil.repository.PrecioOroRepository precioOroRepository;

    public PlazoService(PlazoRepository repository,
                        PlazoMapper plazoMapper,
                        TipoPrendaService tipoPrendaService,
                        PlazoParametroRepository plazoParametroRepository,
                        PlazoParametroMapper plazoParametroMapper,
                        PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository,
                        PlazoHechuraAlhajaMapper plazoHechuraAlhajaMapper,
                        com.ignis.prestamil.repository.PrecioOroRepository precioOroRepository) {
        super(repository);
        this.plazoMapper = plazoMapper;
        this.tipoPrendaService = tipoPrendaService;
        this.plazoParametroRepository = plazoParametroRepository;
        this.plazoParametroMapper = plazoParametroMapper;
        this.plazoHechuraAlhajaRepository = plazoHechuraAlhajaRepository;
        this.plazoHechuraAlhajaMapper = plazoHechuraAlhajaMapper;
        this.precioOroRepository = precioOroRepository;
    }

    /**
     * Crea un nuevo plazo.
     *
     * @param request DTO con los datos del nuevo plazo
     * @return PlazoResponse con los datos del plazo creado
     */
    public PlazoResponse create(PlazoRequest request) {
        // Convertir request a entidad
        Plazo plazo = plazoMapper.toPlazo(request);

        // Asignar tipos de prenda si se proporcionaron
        if (request.getTiposPrenda() != null && !request.getTiposPrenda().isEmpty()) {
            List<TipoPrenda> tiposPrenda = new ArrayList<>();
            for (Integer tipoPrendaId : request.getTiposPrenda()) {
                TipoPrenda tipoPrenda = tipoPrendaService.findById(tipoPrendaId);
                tiposPrenda.add(tipoPrenda);
            }
            plazo.setTiposPrenda(tiposPrenda);
        }

        // Guardar en base de datos (los timestamps se crearán automáticamente)
        Plazo saved = super.save(plazo);

        // Convertir entidad a response y retornar
        return plazoMapper.toPlazoResponse(saved);
    }

    /**
     * Actualiza un plazo existente.
     *
     * @param id      ID del plazo a actualizar
     * @param request DTO con los datos a actualizar
     * @return PlazoResponse con los datos actualizados
     */
    public PlazoResponse update(Long id, PlazoRequest request) {
        // Buscar el plazo existente (si no existe, lanzará ResourceNotFoundException)
        Plazo plazo = super.findById(id);

        // Actualizar los campos (excepto ID y timestamps)
        plazo.setNombre(request.getNombre());
        plazo.setDiasPorPeriodo(request.getDiasPorPeriodo());
        plazo.setNumeroPeriodos(request.getNumeroPeriodos());
        if (request.getActivo() != null) {
            plazo.setActivo(request.getActivo());
        }

        // Actualizar tipos de prenda si se proporcionaron
        if (request.getTiposPrenda() != null) {
            List<TipoPrenda> tiposPrenda = new ArrayList<>();
            if (!request.getTiposPrenda().isEmpty()) {
                for (Integer tipoPrendaId : request.getTiposPrenda()) {
                    TipoPrenda tipoPrenda = tipoPrendaService.findById(tipoPrendaId);
                    tiposPrenda.add(tipoPrenda);
                }
            }
            plazo.setTiposPrenda(tiposPrenda);
        }
        // actualizadoEn se actualizará automáticamente por @UpdateTimestamp

        // Guardar los cambios
        Plazo updated = super.update(plazo);

        // Convertir entidad a response y retornar
        return plazoMapper.toPlazoResponse(updated);
    }

    /**
     * Obtiene los parámetros de préstamo para un plazo, tipo de prenda y sucursal.
     *
     * @param idPlazo      identificador del plazo
     * @param idTipoPrenda identificador del tipo de prenda
     * @param sucursalId   identificador de la sucursal
     * @return PlazoParametroResponse con los parámetros configurados
     * @throws ResourceNotFoundException si no existe configuración para la combinación
     */
    public PlazoParametroResponse getParametrosPlazo(Long idPlazo, Integer idTipoPrenda, Integer sucursalId) {
        PlazoParametroId id = new PlazoParametroId(idPlazo, idTipoPrenda, sucursalId);
        return plazoParametroRepository.findById(id)
                .map(plazoParametroMapper::toPlazoParametroResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PlazoParametro no encontrado: plazo=" + idPlazo + ",tipoPrenda=" + idTipoPrenda + ",sucursal=" + sucursalId));
    }

    /**
     * Lista todos los parámetros de préstamo configurados para un plazo y sucursal.
     *
     * @param plazoId    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return lista de PlazoParametroResponse para la sucursal indicada
     */
    public List<PlazoParametroResponse> getParametrosBySucursal(Long plazoId, Integer sucursalId) {
        return plazoParametroRepository.findByPlazoIdAndSucursalId(plazoId, sucursalId)
                .stream()
                .map(plazoParametroMapper::toPlazoParametroResponse)
                .toList();
    }

    /**
     * Crea o actualiza (upsert) los parámetros para una combinación plazo+tipoPrenda+sucursal.
     *
     * @param plazoId      identificador del plazo
     * @param tipoPrendaId identificador del tipo de prenda
     * @param sucursalId   identificador de la sucursal
     * @param request      DTO con los valores a guardar
     * @return PlazoParametroResponse con los datos guardados
     */
    public PlazoParametroResponse guardarParametro(Long plazoId, Integer tipoPrendaId,
                                                    Integer sucursalId, PlazoParametroRequest request) {
        PlazoParametroId id = new PlazoParametroId(plazoId, tipoPrendaId, sucursalId);
        PlazoParametro entity = plazoParametroRepository.findById(id)
                .orElseGet(() -> {
                    // Crear nueva entidad con clave compuesta
                    PlazoParametro nuevo = new PlazoParametro();
                    nuevo.setPlazoId(plazoId);
                    nuevo.setTipoPrendaId(tipoPrendaId);
                    nuevo.setSucursalId(sucursalId);
                    return nuevo;
                });
        // Actualizar campos editables desde el request
        plazoParametroMapper.actualizarDesdeRequest(entity, request);
        PlazoParametro guardado = plazoParametroRepository.save(entity);
        return plazoParametroMapper.toPlazoParametroResponse(guardado);
    }

    // =========================================================================
    // Métodos para tabla de alhajas (plazo_hechura_alhaja)
    // =========================================================================

    /**
     * Lista la tabla de hechuras de alhajas para un plazo y sucursal.
     *
     * @param idPlazo    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @return lista de PlazoHechuraAlhajaResponse ordenada por kilataje y hechura
     */
    public List<PlazoHechuraAlhajaResponse> getTablaAlhajas(Integer idPlazo, Integer sucursalId) {
        return plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(idPlazo, sucursalId)
                .stream()
                .map(plazoHechuraAlhajaMapper::toResponse)
                .toList();
    }

    /**
     * Actualiza el precio base de una hechura específica y recalcula el precio de préstamo:
     *   precioPrestamo = precioBase * (1 + porcAumento)
     *
     * @param idPlazo    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @param kilataje   kilataje del oro (p.ej. 10, 14, 18)
     * @param hechura    clave de hechura ("F", "N" o "E")
     * @param precioBase nuevo precio base en pesos
     * @return PlazoHechuraAlhajaResponse actualizado
     * @throws ResourceNotFoundException si no existe el registro para la combinación dada
     */
    public PlazoHechuraAlhajaResponse actualizarPrecioBase(Integer idPlazo, Integer sucursalId,
                                                            Integer kilataje, String hechura,
                                                            BigDecimal precioBase) {
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, kilataje, hechura);
        PlazoHechuraAlhaja entity = plazoHechuraAlhajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PlazoHechuraAlhaja no encontrado: idPlazo=" + idPlazo + ",sucursal=" + sucursalId + ",kilataje=" + kilataje + ",hechura=" + hechura));
        entity.setPrecioBase(precioBase);
        BigDecimal precioPrestamo = precioBase
                .multiply(BigDecimal.ONE.add(entity.getPorcAumento().divide(CIEN, 10, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        entity.setPrecioPrestamo(precioPrestamo);
        return plazoHechuraAlhajaMapper.toResponse(plazoHechuraAlhajaRepository.save(entity));
    }

    /**
     * Crea una nueva combinación de alhaja para un plazo y sucursal específicos.
     * Calcula automáticamente el precioPrestamo a partir de precioBase * (1 + porcAumento).
     *
     * @param idPlazo    identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @param request    datos de la nueva alhaja (kilataje, hechura, precioBase, porcAumento)
     * @return PlazoHechuraAlhajaResponse de la alhaja creada
     * @throws BadRequestException si ya existe la combinación plazo/sucursal/kilataje/hechura
     */
    public PlazoHechuraAlhajaResponse crearAlhaja(Integer idPlazo, Integer sucursalId, PlazoHechuraAlhajaRequest request) {
        // 1. Validar duplicado
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, request.getKilataje(), request.getHechura());
        if (plazoHechuraAlhajaRepository.existsById(id)) {
            throw new BadRequestException("Ya existe una combinación " + request.getKilataje() + "K/" + request.getHechura() + " para este plazo/sucursal");
        }
        // 2. Construir entidad desde el request usando el mapper
        PlazoHechuraAlhaja entity = plazoHechuraAlhajaMapper.toEntity(request, idPlazo, sucursalId);
        // 3. tablaPrestamoId = 1 por defecto (iteración 1 del módulo)
        entity.setTablaPrestamoId(1);
        // 4. Calcular precioPrestamo = precioBase * (1 + porcAumento / 100)
        BigDecimal precioPrestamo = request.getPrecioBase()
                .multiply(BigDecimal.ONE.add(request.getPorcAumento().divide(CIEN, 10, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        entity.setPrecioPrestamo(precioPrestamo);
        // 5. Guardar y mapear a response
        return plazoHechuraAlhajaMapper.toResponse(plazoHechuraAlhajaRepository.save(entity));
    }

    /**
     * Recalcula precioBase y precioPrestamo para TODOS los registros del plazo+sucursal usando
     * un precio base de oro de 24 kilates por onza troy.
     *   precioBaseKilate = (precioBaseOro / 24) * kilataje * 31.1035
     *   precioPrestamo   = precioBaseKilate * (1 + porcAumento)
     *
     * @param idPlazo      identificador del plazo
     * @param sucursalId   identificador de la sucursal
     * @param precioBaseOro precio del oro de 24K por onza troy en pesos
     * @throws ResourceNotFoundException si no hay registros para la combinación
     * @throws BadRequestException       si precioBaseOro es menor o igual a cero
     */
    public void actualizarTodosPrecios(Integer idPlazo, Integer sucursalId, BigDecimal precioBaseOro) {
        if (precioBaseOro == null || precioBaseOro.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("precioBaseOro debe ser mayor que cero");
        }
        List<PlazoHechuraAlhaja> registros =
                plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(idPlazo, sucursalId);
        if (registros.isEmpty()) {
            throw new ResourceNotFoundException(
                    "PlazoHechuraAlhaja: no hay registros para plazo=" + idPlazo + ", sucursal=" + sucursalId);
        }
        com.ignis.prestamil.model.PrecioOro precio = precioOroRepository.findBySucursalId(sucursalId).orElse(null);
        int baseKilataje = precio != null && precio.getBaseKilataje() != null ? precio.getBaseKilataje() : 24;
        BigDecimal factorFundir = precio != null && precio.getFactorFundir() != null ? precio.getFactorFundir() : new BigDecimal("90.0000");
        BigDecimal factorNormal = precio != null && precio.getFactorNormal() != null ? precio.getFactorNormal() : new BigDecimal("100.0000");
        BigDecimal factorEspecial = precio != null && precio.getFactorEspecial() != null ? precio.getFactorEspecial() : new BigDecimal("110.0000");
        recalcularRegistros(registros, precioBaseOro, baseKilataje, factorFundir, factorNormal, factorEspecial);
        plazoHechuraAlhajaRepository.saveAll(registros);
    }

    /**
     * Aplica el cálculo de oro a una lista de registros (sin persistir):
     *   precioBase     = (precioGramoBase / baseKilataje) * kilataje
     *   precioPrestamo = precioBase * (1 + porcAumento / 100)
     *
     * @param registros      registros de hechura/kilataje a recalcular
     * @param precioGramoBase precio del oro por gramo al kilataje base
     * @param baseKilataje   kilataje de referencia del precio ingresado (21 o 24)
     */
    private void recalcularRegistros(List<PlazoHechuraAlhaja> registros,
                                     BigDecimal precioGramoBase, int baseKilataje,
                                     BigDecimal factorFundir, BigDecimal factorNormal, BigDecimal factorEspecial) {
        BigDecimal base = new BigDecimal(baseKilataje);
        BigDecimal precioPorKilatePuro = precioGramoBase.divide(base, 10, RoundingMode.HALF_UP);
        for (PlazoHechuraAlhaja r : registros) {
            // factor de hechura (calidad): Fundir 90%, Normal 100%, Especial 110% (configurable)
            BigDecimal factorHechura = factorPorHechura(r.getId().getHechura(),
                    factorFundir, factorNormal, factorEspecial)
                    .divide(CIEN, 10, RoundingMode.HALF_UP);
            // precioBase = (precioGramoBase / baseKilataje) * kilataje * factorHechura
            BigDecimal precioBase = precioPorKilatePuro
                    .multiply(new BigDecimal(r.getId().getKilataje()))
                    .multiply(factorHechura)
                    .setScale(4, RoundingMode.HALF_UP);
            r.setPrecioBase(precioBase);
            r.setPrecioPrestamo(precioBase
                    .multiply(BigDecimal.ONE.add(r.getPorcAumento().divide(CIEN, 10, RoundingMode.HALF_UP)))
                    .setScale(4, RoundingMode.HALF_UP));
        }
    }

    /**
     * Resuelve el factor de hechura según la clave. Acepta "F"/"N"/"E" o "HF"/"HN"/"HE"
     * (se evalúa el último carácter). Devuelve el porcentaje (p.ej. 90, 100, 110).
     */
    private BigDecimal factorPorHechura(String hechura, BigDecimal factorFundir,
                                        BigDecimal factorNormal, BigDecimal factorEspecial) {
        String h = hechura != null ? hechura.trim().toUpperCase() : "";
        if (h.endsWith("F")) return factorFundir;
        if (h.endsWith("E")) return factorEspecial;
        return factorNormal; // por defecto / "N"
    }

    /** Devuelve el primer argumento no nulo. */
    private static BigDecimal primerNoNulo(BigDecimal... valores) {
        for (BigDecimal v : valores) {
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Punto de entrada ÚNICO para el precio del oro: persiste el precio del gramo base
     * y los factores de hechura para la sucursal y recalcula TODAS las tablas de
     * hechura/kilataje de esa sucursal (todos los plazos) en una sola operación.
     * Control global de "Precio del Oro" en la pantalla de Plazos y Periodos.
     *
     * @param sucursalId identificador de la sucursal
     * @param request    precio del gramo base, kilataje de referencia, modo de cálculo y factores
     * @param usuario    nombre del usuario que realiza el cambio (auditoría)
     * @return PrecioOroResponse con el precio vigente persistido
     * @throws BadRequestException si el precio es nulo o menor o igual a cero
     */
    public com.ignis.prestamil.response.PrecioOroResponse recalcularTodasLasTablas(
            Integer sucursalId, PrecioOroRequest request, String usuario) {
        if (request.getPrecioGramoBase() == null || request.getPrecioGramoBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("precioGramoBase debe ser mayor que cero");
        }
        int baseKilataje = request.getBaseKilataje() != null ? request.getBaseKilataje() : 24;
        String calcularSobre = request.getCalcularSobre() != null ? request.getCalcularSobre() : "PRESTAMO";

        // 1. Cargar/crear el precio del oro y resolver factores efectivos
        //    (los del request mandan; si vienen nulos se conservan los vigentes).
        com.ignis.prestamil.model.PrecioOro precio = precioOroRepository.findBySucursalId(sucursalId)
                .orElseGet(() -> {
                    com.ignis.prestamil.model.PrecioOro nuevo = new com.ignis.prestamil.model.PrecioOro();
                    nuevo.setSucursalId(sucursalId);
                    return nuevo;
                });
        BigDecimal factorFundir = primerNoNulo(request.getFactorFundir(), precio.getFactorFundir(), new BigDecimal("90.0000"));
        BigDecimal factorNormal = primerNoNulo(request.getFactorNormal(), precio.getFactorNormal(), new BigDecimal("100.0000"));
        BigDecimal factorEspecial = primerNoNulo(request.getFactorEspecial(), precio.getFactorEspecial(), new BigDecimal("110.0000"));

        // 2. Recalcular todas las tablas de la sucursal aplicando el factor de hechura
        List<PlazoHechuraAlhaja> registros = plazoHechuraAlhajaRepository.findByIdSucursalId(sucursalId);
        if (!registros.isEmpty()) {
            recalcularRegistros(registros, request.getPrecioGramoBase(), baseKilataje,
                    factorFundir, factorNormal, factorEspecial);
            plazoHechuraAlhajaRepository.saveAll(registros);
        }

        // 3. Persistir (upsert) el precio del oro vigente para la sucursal
        precio.setPrecioGramo24k(request.getPrecioGramoBase());
        precio.setBaseKilataje(baseKilataje);
        precio.setCalcularSobre(calcularSobre);
        precio.setFactorFundir(factorFundir);
        precio.setFactorNormal(factorNormal);
        precio.setFactorEspecial(factorEspecial);
        precio.setActualizadoPor(usuario);
        com.ignis.prestamil.model.PrecioOro guardado = precioOroRepository.save(precio);
        log.info("Precio del oro actualizado: sucursal={} precioGramo={} base={}K factores={}/{}/{} registros={}",
                sucursalId, request.getPrecioGramoBase(), baseKilataje,
                factorFundir, factorNormal, factorEspecial, registros.size());
        return toPrecioOroResponse(guardado);
    }

    /**
     * Obtiene el precio del oro vigente para una sucursal.
     *
     * @param sucursalId identificador de la sucursal
     * @return PrecioOroResponse vigente
     * @throws ResourceNotFoundException si no hay precio configurado
     */
    @Transactional(readOnly = true)
    public com.ignis.prestamil.response.PrecioOroResponse getPrecioOro(Integer sucursalId) {
        return precioOroRepository.findBySucursalId(sucursalId)
                .map(this::toPrecioOroResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay precio de oro configurado para la sucursal " + sucursalId));
    }

    private com.ignis.prestamil.response.PrecioOroResponse toPrecioOroResponse(com.ignis.prestamil.model.PrecioOro p) {
        com.ignis.prestamil.response.PrecioOroResponse r = new com.ignis.prestamil.response.PrecioOroResponse();
        r.setId(p.getId());
        r.setSucursalId(p.getSucursalId());
        r.setPrecioGramo24k(p.getPrecioGramo24k());
        r.setCalcularSobre(p.getCalcularSobre());
        r.setBaseKilataje(p.getBaseKilataje());
        r.setFactorFundir(p.getFactorFundir());
        r.setFactorNormal(p.getFactorNormal());
        r.setFactorEspecial(p.getFactorEspecial());
        r.setActualizadoEn(p.getActualizadoEn());
        r.setActualizadoPor(p.getActualizadoPor());
        return r;
    }

    /**
     * Calcula el avalúo que aparece en el contrato a partir del monto del préstamo
     * y los parámetros del plazo. Si la sucursal no usa avalúo real (o el porcentaje
     * configurado es cero/nulo), el avalúo del contrato es igual al monto del préstamo.
     *
     * Fórmula: avaluoContrato = montoPrestamo × (1 + porcPrestamoSAvaluoReal / 100)
     *
     * @param montoPrestamo monto efectivamente prestado al cliente
     * @param parametro     parámetros del plazo/tipo de prenda/sucursal
     * @return avalúo a imprimir en el contrato, con escala 2 (HALF_UP)
     */
    public BigDecimal calcularAvaluoContrato(BigDecimal montoPrestamo, PlazoParametro parametro) {
        if (!parametro.getUsaAvaluoReal()
            || parametro.getPorcPrestamoSAvaluoReal() == null
            || parametro.getPorcPrestamoSAvaluoReal().compareTo(BigDecimal.ZERO) == 0) {
            return montoPrestamo;
        }
        BigDecimal factor = BigDecimal.ONE.add(
            parametro.getPorcPrestamoSAvaluoReal()
                .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
        );
        return montoPrestamo.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
