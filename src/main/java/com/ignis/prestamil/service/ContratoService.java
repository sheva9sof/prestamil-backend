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

    public ContratoService(ContratoRepository repository,
                           ClienteRepository clienteRepository,
                           PlazoRepository plazoRepository,
                           TurnoRepository turnoRepository,
                           UsuarioRepository usuarioRepository,
                           TipoPrendaRepository tipoPrendaRepository,
                           CatValorPrendaRepository catValorPrendaRepository,
                           ContratoMapper contratoMapper) {
        super(repository);
        this.clienteRepository = clienteRepository;
        this.plazoRepository = plazoRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.tipoPrendaRepository = tipoPrendaRepository;
        this.catValorPrendaRepository = catValorPrendaRepository;
        this.contratoMapper = contratoMapper;
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

        // 6. Construir partidas y acumular totales
        List<PartidaContrato> partidas = new ArrayList<>();
        BigDecimal totalPrestamo = BigDecimal.ZERO;
        BigDecimal totalAvaluo = BigDecimal.ZERO;

        for (int i = 0; i < request.getPartidas().size(); i++) {
            PartidaContratoRequest pr = request.getPartidas().get(i);
            PartidaContrato partida = buildPartida(pr, i + 1);
            totalPrestamo = totalPrestamo.add(pr.getMontoPrestamo());
            totalAvaluo = totalAvaluo.add(pr.getAvaluoContrato());
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

    private PartidaContrato buildPartida(PartidaContratoRequest pr, int numPartida) {
        TipoPrenda tipoPrenda = tipoPrendaRepository.findById(pr.getIdTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TipoPrenda no encontrado: " + pr.getIdTipoPrenda()));

        PartidaContrato partida = new PartidaContrato();
        partida.setNumPartida(numPartida);
        partida.setTipoPrenda(tipoPrenda);
        partida.setDescripcion(pr.getDescripcion());
        partida.setClavePrenda(pr.getClavePrenda());
        partida.setCantidad(pr.getCantidad() != null ? pr.getCantidad() : 1);
        partida.setPesoGramos(pr.getPesoGramos());
        partida.setKilataje(pr.getKilataje());
        partida.setHechura(pr.getHechura());
        partida.setPrecioXGramo(pr.getPrecioXGramo());
        partida.setAvaluoReal(pr.getAvaluoReal());
        partida.setAvaluoContrato(pr.getAvaluoContrato());
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
}
