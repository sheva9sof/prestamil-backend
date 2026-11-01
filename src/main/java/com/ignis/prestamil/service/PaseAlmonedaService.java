package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.service.calculo.EstatusContratoResolver;
import com.ignis.prestamil.util.Constantes;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pase de almoneda diario (F11, RN-04 / RN-08). Por cada dia de la sucursal se persisten los
 * cambios de estatus que las fechas dictan:
 *
 * <ul>
 *   <li>VIGENTE con gracia vencida -> VENCIDO (sin movimiento; el cambio es de calendario).</li>
 *   <li>VENCIDO cuyo dia de comercializacion llego -> EN_VENTA con movimiento PV (RN-08).</li>
 * </ul>
 *
 * <p>No depende de que alguien abra turno: {@link #ejecutarPendientes()} corre al arrancar, a las
 * 00:05, al abrir turno y al consultar resultados, y se pone al dia un dia a la vez desde el ultimo
 * pase, cada uno con su fecha. Asi un contrato atrasado pasa por "cartera vencida" en su dia antes
 * de "pase a venta". Cada dia es idempotente: la clave unica {@code (id_sucursal, fecha)} y el
 * bloqueo de la sucursal impiden correrlo dos veces, aunque dos llamadas lleguen a la vez.</p>
 */
@Slf4j
@Service
public class PaseAlmonedaService {

    private static final Set<EstatusContrato> ESTATUS_CANDIDATOS =
            EnumSet.of(EstatusContrato.VIGENTE, EstatusContrato.VENCIDO);

    private final ContratoRepository contratoRepository;
    private final MovimientoContratoRepository movimientoContratoRepository;
    private final PaseAlmonedaRepository paseAlmonedaRepository;
    private final PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository;
    private final PlazoParametroRepository plazoParametroRepository;
    private final SucursalRepository sucursalRepository;
    private final SucursalService sucursalService;
    private final EntityManager entityManager;
    private final Clock clock;
    // Un dia = una transaccion propia (REQUIRES_NEW): si un dia falla, los anteriores quedan
    // guardados, y abrir turno no se cae por un error del pase.
    private final TransactionTemplate transaccionPorDia;

    public PaseAlmonedaService(ContratoRepository contratoRepository,
                               MovimientoContratoRepository movimientoContratoRepository,
                               PaseAlmonedaRepository paseAlmonedaRepository,
                               PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository,
                               PlazoParametroRepository plazoParametroRepository,
                               SucursalRepository sucursalRepository,
                               SucursalService sucursalService,
                               EntityManager entityManager,
                               Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.contratoRepository = contratoRepository;
        this.movimientoContratoRepository = movimientoContratoRepository;
        this.paseAlmonedaRepository = paseAlmonedaRepository;
        this.paseAlmonedaDetalleRepository = paseAlmonedaDetalleRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.sucursalRepository = sucursalRepository;
        this.sucursalService = sucursalService;
        this.entityManager = entityManager;
        this.clock = clock;
        this.transaccionPorDia = new TransactionTemplate(transactionManager);
        this.transaccionPorDia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Pone al dia el pase de almoneda de la sucursal configurada. No lanza excepcion: un error se
     * registra en el log y se reintenta en la siguiente llamada (arranque, 00:05, turno o consulta).
     *
     * @return dias que se corrieron en esta llamada (0 si ya estaba al dia o no hay sucursal)
     */
    public int ejecutarPendientes() {
        return sucursalService.findUnique()
                .map(Sucursal::getId)
                .map(this::ejecutarPendientes)
                .orElse(0);
    }

    /**
     * Corre un pase por cada dia desde el siguiente al ultimo pase de la sucursal hasta hoy, en
     * orden y cada uno con su propia fecha. Si la sucursal nunca ha corrido un pase, solo corre el
     * de hoy. Se detiene en el primer dia que falle para no saltarse dias.
     *
     * @param sucursalId sucursal a poner al dia
     * @return dias que se corrieron en esta llamada
     */
    public int ejecutarPendientes(Integer sucursalId) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(sucursalId)
                .map(ultimo -> ultimo.getFecha().plusDays(1))
                .orElse(hoy);
        int corridos = 0;
        for (LocalDate dia = desde; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            LocalDate fecha = dia;
            try {
                if (Boolean.TRUE.equals(transaccionPorDia.execute(estado -> ejecutar(sucursalId, fecha)))) {
                    corridos++;
                }
            } catch (RuntimeException e) {
                log.error("Pase de almoneda de la sucursal {} del {} fallo; se reintenta en la siguiente llamada",
                        sucursalId, fecha, e);
                break;
            }
        }
        if (corridos > 1) {
            log.info("Pase de almoneda de la sucursal {} puesto al dia: {} dias hasta {}", sucursalId, corridos, hoy);
        }
        return corridos;
    }

    /**
     * Ejecuta el pase de almoneda de un dia para la sucursal. Si ese dia ya corrio no hace nada.
     * Debe correr dentro de una transaccion: bloquea la fila de la sucursal hasta el commit para que
     * una segunda ejecucion simultanea espere y luego vea el dia ya registrado.
     *
     * <p>La operacion no se detiene mientras corre: los cobros validan el estatus por fechas, asi que
     * da igual si el pase del dia ya se aplico. Lo que si se cuida es no pisar un cobro que se hizo a
     * mitad del pase: cada contrato que las fechas mandan cambiar se relee bloqueado antes de tocarlo
     * (ver {@link #releerConBloqueo}).</p>
     *
     * @param sucursalId sucursal donde se corre
     * @param fecha      dia del pase; el estatus de cada contrato se calcula contra esta fecha
     * @return {@code true} si corrio, {@code false} si ese dia ya tenia pase
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean ejecutar(Integer sucursalId, LocalDate fecha) {
        sucursalRepository.findWithLockById(sucursalId);
        if (paseAlmonedaRepository.existsBySucursalIdAndFecha(sucursalId, fecha)) {
            return false;
        }

        // Crea primero la bitacora para poder referenciarla en pase_almoneda_detalle (C-11).
        PaseAlmoneda bitacora = new PaseAlmoneda();
        bitacora.setSucursalId(sucursalId);
        bitacora.setFecha(fecha);
        bitacora.setEjecutadoEn(LocalDateTime.now(clock));
        bitacora.setContratosAVencido(0);
        bitacora.setContratosAVenta(0);
        bitacora.setMontoPasadoAVenta(BigDecimal.ZERO);
        bitacora = paseAlmonedaRepository.save(bitacora);

        List<Contrato> candidatos = contratoRepository.findByEstatusOrderByFechaVencimientoAsc(
                EstatusContrato.VIGENTE);
        candidatos.addAll(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(
                EstatusContrato.VENCIDO));

        int aVencido = 0;
        int aVenta = 0;
        BigDecimal montoPasadoAVenta = BigDecimal.ZERO;

        for (Contrato candidato : candidatos) {
            if (!sucursalId.equals(candidato.getSucursalId())) {
                continue;
            }
            if (!requiereCambio(candidato, fecha)) {
                continue;
            }
            // La lista de arriba es solo preseleccion: se leyo sin bloqueo y un cobro pudo cambiar el
            // contrato despues. Se relee bloqueado y se vuelve a decidir con los datos frescos.
            Contrato contrato = releerConBloqueo(candidato);
            if (contrato == null || !requiereCambio(contrato, fecha)) {
                log.info("Pase de almoneda del {}: el contrato {} cambio durante el pase; se omite",
                        fecha, candidato.getFolio());
                continue;
            }
            EstatusOperativo deseado = estatusDerivadoPorFecha(contrato, fecha);
            EstatusContrato origen = contrato.getEstatus();
            if (origen == EstatusContrato.VIGENTE && deseado == EstatusOperativo.VENCIDO) {
                contrato.setEstatus(EstatusContrato.VENCIDO);
                contratoRepository.save(contrato);
                registrarDetalleVencido(bitacora, contrato);
                aVencido++;
            } else if (deseado == EstatusOperativo.EN_VENTA
                    && (origen == EstatusContrato.VIGENTE || origen == EstatusContrato.VENCIDO)) {
                // Con la puesta al dia diaria un VIGENTE no deberia llegar aqui: pasa antes por
                // VENCIDO en su dia. Si llega (primer pase de la sucursal, o una cancelacion que
                // regreso fechas viejas), se registra tambien en cartera vencida para que no se
                // brinque la lista de cobranza.
                if (origen == EstatusContrato.VIGENTE) {
                    registrarDetalleVencido(bitacora, contrato);
                    aVencido++;
                }
                registrarMovimientoPaseAVenta(contrato, fecha, origen);
                contrato.setEstatus(EstatusContrato.EN_VENTA);
                contratoRepository.save(contrato);
                registrarDetalleEnVenta(bitacora, contrato);
                aVenta++;
                montoPasadoAVenta = montoPasadoAVenta.add(contrato.getMontoPrestamo());
            }
        }

        bitacora.setContratosAVencido(aVencido);
        bitacora.setContratosAVenta(aVenta);
        bitacora.setMontoPasadoAVenta(montoPasadoAVenta);
        paseAlmonedaRepository.save(bitacora);
        return true;
    }

    /**
     * ¿Las fechas piden mover hoy el contrato? VIGENTE a VENCIDO, o VIGENTE/VENCIDO a EN_VENTA. Un
     * contrato en cualquier otro estatus (finiquitado, cancelado, ya en venta) no se toca.
     */
    private boolean requiereCambio(Contrato contrato, LocalDate fecha) {
        EstatusContrato origen = contrato.getEstatus();
        if (!ESTATUS_CANDIDATOS.contains(origen)) {
            return false;
        }
        EstatusOperativo deseado = estatusDerivadoPorFecha(contrato, fecha);
        return deseado == EstatusOperativo.EN_VENTA
                || (origen == EstatusContrato.VIGENTE && deseado == EstatusOperativo.VENCIDO);
    }

    /**
     * Relee el contrato con {@code SELECT ... FOR UPDATE}, el mismo bloqueo que toman los cobros y las
     * cancelaciones. La preseleccion se lee sin bloqueo, y si en ese lapso un cajero refrenda,
     * finiquita o cancela, guardar la copia vieja pisaria el cobro: Hibernate actualiza todas las
     * columnas y regresaria el vencimiento y el saldo anteriores, ademas de poner EN_VENTA y un PV.
     * Con el bloqueo, un cobro en curso termina antes de que el pase decida, y uno que llega despues
     * espera al commit del pase y valida contra el estatus ya persistido.
     *
     * <p>El candidato se saca del contexto de persistencia antes de releer: si siguiera ahi, Hibernate
     * bloquearia la fila pero devolveria la misma instancia con los datos de la preseleccion.</p>
     *
     * @return el contrato fresco y bloqueado hasta el commit del dia, o {@code null} si ya no existe
     */
    private Contrato releerConBloqueo(Contrato candidato) {
        entityManager.detach(candidato);
        return contratoRepository.findWithLockById(candidato.getId()).orElse(null);
    }

    /**
     * Fila de "Cartera vencida" (C-11): a nivel contrato, sin partida, con el vencimiento y el saldo
     * de ese dia. TODO G-08: Jorge dijo que el contrato entra en cartera vencida el dia siguiente al
     * vencimiento ("en su dia 29"), aunque siga en gracia; hoy la transicion se dispara al rebasar la
     * gracia (F11). Si se decide adoptar el criterio de Jorge, aqui tambien hay que insertar una fila
     * cuando {@code fecha = vencimiento + 1} aunque el contrato siga en gracia.
     */
    private void registrarDetalleVencido(PaseAlmoneda bitacora, Contrato contrato) {
        paseAlmonedaDetalleRepository.save(detalle(bitacora, contrato, null, TipoCambioPase.VENCIDO));
    }

    /**
     * Filas de "Pase a venta" (C-11): una por cada partida en operacion (OP) del contrato, que son
     * las que siguen en boveda y el gerente debe sacar. Si el contrato no trae partidas cargadas se
     * registra una fila sin partida para no perder trazabilidad del cambio de estatus.
     */
    private void registrarDetalleEnVenta(PaseAlmoneda bitacora, Contrato contrato) {
        if (contrato.getPartidas() == null || contrato.getPartidas().isEmpty()) {
            paseAlmonedaDetalleRepository.save(detalle(bitacora, contrato, null, TipoCambioPase.EN_VENTA));
            return;
        }
        for (PartidaContrato partida : contrato.getPartidas()) {
            if (partida.getEstatus() == EstatusPartida.OP) {
                paseAlmonedaDetalleRepository.save(detalle(bitacora, contrato, partida, TipoCambioPase.EN_VENTA));
            }
        }
    }

    /** Fila de detalle con el vencimiento y el saldo del contrato el dia del pase (changeset 076). */
    private PaseAlmonedaDetalle detalle(PaseAlmoneda bitacora, Contrato contrato, PartidaContrato partida,
                                        TipoCambioPase tipo) {
        PaseAlmonedaDetalle detalle = new PaseAlmonedaDetalle();
        detalle.setPase(bitacora);
        detalle.setContrato(contrato);
        detalle.setPartida(partida);
        detalle.setTipoCambio(tipo);
        detalle.setFechaVencimiento(contrato.getFechaVencimiento());
        detalle.setSaldoCapital(contrato.getSaldoCapital());
        return detalle;
    }

    /**
     * Estatus operativo que las fechas (vencimiento + gracia + comercializacion) dictan para el
     * contrato en el dia del pase. Delega en {@link EstatusContratoResolver#estatusPorFechas} para no
     * duplicar reglas.
     */
    private EstatusOperativo estatusDerivadoPorFecha(Contrato contrato, LocalDate fecha) {
        int diasGracia = resolverDiasGracia(contrato);
        LocalDate comercializacion = contrato.getFechaComercializacion() != null
                ? contrato.getFechaComercializacion()
                : contrato.getFechaVencimiento().plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION);
        return EstatusContratoResolver.estatusPorFechas(
                contrato.getFechaVencimiento(), comercializacion, diasGracia, fecha);
    }

    /**
     * Dias de gracia efectivos: snapshot del contrato (RN-04 congelado al empeñar) o, si es nulo
     * en contratos previos a changeset 026, el {@code PlazoParametro} vigente del primer tipo de
     * prenda del contrato. 0 cuando no hay nada.
     */
    private int resolverDiasGracia(Contrato contrato) {
        if (contrato.getSnapDiasGraciaSancion() != null) {
            return contrato.getSnapDiasGraciaSancion();
        }
        PlazoParametro parametro = plazoParametroVigente(contrato);
        if (parametro != null && parametro.getDiasGraciaSinInteres() != null) {
            return parametro.getDiasGraciaSinInteres();
        }
        return 0;
    }

    private PlazoParametro plazoParametroVigente(Contrato contrato) {
        if (contrato.getPlazo() == null || contrato.getPartidas() == null
                || contrato.getPartidas().isEmpty()) {
            return null;
        }
        PartidaContrato primera = contrato.getPartidas().get(0);
        if (primera.getTipoPrenda() == null) {
            return null;
        }
        return plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(
                contrato.getPlazo().getId(), primera.getTipoPrenda().getId(),
                contrato.getSucursalId()).orElse(null);
    }

    /**
     * Movimiento PV (pase a venta, RN-25/RN-20). Importe en cero: solo documenta el cambio de
     * estatus y las fechas de antes/despues. Es automatico: no lleva turno ni usuario (changeset
     * 078) y no se puede cancelar. Su fecha es la del dia del pase; si es el pase de hoy, la hora
     * real de la ejecucion.
     */
    private void registrarMovimientoPaseAVenta(Contrato contrato, LocalDate fecha, EstatusContrato origen) {
        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTipo(TipoMovimiento.PV);
        mov.setMonto(BigDecimal.ZERO);
        mov.setInteres(BigDecimal.ZERO);
        mov.setSancion(BigDecimal.ZERO);
        mov.setAbonoCapital(BigDecimal.ZERO);
        mov.setSemanasVencidas(0);
        mov.setDiasGraciaUsados(0);
        mov.setPorcDescuentoInteres(BigDecimal.ZERO);
        mov.setImporteDescuento(BigDecimal.ZERO);
        mov.setIva(BigDecimal.ZERO);
        mov.setCancelado(false);
        LocalDateTime ahora = LocalDateTime.now(clock);
        mov.setFecha(fecha.equals(ahora.toLocalDate()) ? ahora : fecha.atStartOfDay());

        mov.setSaldoAnterior(contrato.getSaldoCapital());
        mov.setFechaContratoAnterior(contrato.getFechaContrato());
        mov.setFechaVencAnterior(contrato.getFechaVencimiento());
        mov.setEstatusAnterior(origen);
        mov.setNumRefrendosAnterior(contrato.getNumRefrendos());

        mov.setSaldoNuevo(contrato.getSaldoCapital());
        mov.setFechaContratoNueva(contrato.getFechaContrato());
        mov.setFechaVencNueva(contrato.getFechaVencimiento());
        mov.setEstatusNuevo(EstatusContrato.EN_VENTA);

        movimientoContratoRepository.save(mov);
    }
}
