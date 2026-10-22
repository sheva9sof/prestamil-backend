package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.service.calculo.EstatusContratoResolver;
import com.ignis.prestamil.util.Constantes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pase de almoneda diario (F11, RN-04 / RN-08). Al iniciar el primer turno del dia de la sucursal
 * se persisten los cambios de estatus que las fechas dictan:
 *
 * <ul>
 *   <li>VIGENTE con gracia vencida -> VENCIDO (sin movimiento; el cambio es de calendario).</li>
 *   <li>VENCIDO cuyo dia de comercializacion llego -> EN_VENTA con movimiento PV (RN-08).</li>
 * </ul>
 *
 * <p>Es idempotente: la clave unica {@code (id_sucursal, fecha)} en {@code pase_almoneda} impide
 * una segunda corrida el mismo dia. No expone endpoints y siempre opera sobre la fecha del
 * servidor; no se puede pedir el pase de otra fecha (Jorge, F11).</p>
 */
@Service
@RequiredArgsConstructor
public class PaseAlmonedaService {

    private static final Set<EstatusContrato> ESTATUS_CANDIDATOS =
            EnumSet.of(EstatusContrato.VIGENTE, EstatusContrato.VENCIDO);

    private final ContratoRepository contratoRepository;
    private final MovimientoContratoRepository movimientoContratoRepository;
    private final PaseAlmonedaRepository paseAlmonedaRepository;
    private final PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository;
    private final PlazoParametroRepository plazoParametroRepository;

    /**
     * Ejecuta el pase de almoneda del dia para la sucursal. Si ya corrio hoy no hace nada.
     *
     * @param sucursalId sucursal donde se dispara (de {@code SucursalService.findUnique()})
     * @param turno      turno que origina la ejecucion (el que se acaba de abrir)
     */
    @Transactional
    public void ejecutar(Integer sucursalId, Turno turno) {
        LocalDate hoy = LocalDate.now();
        if (paseAlmonedaRepository.existsBySucursalIdAndFecha(sucursalId, hoy)) {
            return;
        }

        // Crea primero la bitacora para poder referenciarla en pase_almoneda_detalle (C-11).
        PaseAlmoneda bitacora = new PaseAlmoneda();
        bitacora.setSucursalId(sucursalId);
        bitacora.setFecha(hoy);
        bitacora.setEjecutadoEn(LocalDateTime.now());
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

        for (Contrato contrato : candidatos) {
            if (!sucursalId.equals(contrato.getSucursalId())) {
                continue;
            }
            if (!ESTATUS_CANDIDATOS.contains(contrato.getEstatus())) {
                continue;
            }
            EstatusOperativo deseado = estatusDerivadoPorFecha(contrato, hoy);
            EstatusContrato origen = contrato.getEstatus();
            if (origen == EstatusContrato.VIGENTE && deseado == EstatusOperativo.VENCIDO) {
                contrato.setEstatus(EstatusContrato.VENCIDO);
                contratoRepository.save(contrato);
                registrarDetalleVencido(bitacora, contrato);
                aVencido++;
            } else if (deseado == EstatusOperativo.EN_VENTA
                    && (origen == EstatusContrato.VIGENTE || origen == EstatusContrato.VENCIDO)) {
                // Si un contrato VIGENTE llega tarde al pase (gracia + comercializacion ya pasados),
                // tambien aterriza en EN_VENTA en una sola corrida: lo pide la nota de F11
                // ("la siguiente ejecucion evalua todo contra la fecha actual").
                registrarMovimientoPaseAVenta(contrato, turno, origen);
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
    }

    /**
     * Fila de "Cartera vencida" (C-11): a nivel contrato, sin partida. TODO G-08: Jorge dijo que el
     * contrato entra en cartera vencida el dia siguiente al vencimiento ("en su dia 29"), aunque
     * siga en gracia; hoy la transicion se dispara al rebasar la gracia (F11). Si se decide
     * adoptar el criterio de Jorge, aqui tambien hay que insertar una fila cuando
     * {@code hoy = vencimiento + 1} aunque el contrato siga en gracia.
     */
    private void registrarDetalleVencido(PaseAlmoneda bitacora, Contrato contrato) {
        PaseAlmonedaDetalle detalle = new PaseAlmonedaDetalle();
        detalle.setPase(bitacora);
        detalle.setContrato(contrato);
        detalle.setTipoCambio(TipoCambioPase.VENCIDO);
        paseAlmonedaDetalleRepository.save(detalle);
    }

    /**
     * Filas de "Pase a venta" (C-11): una por cada partida del contrato (el gerente las saca de
     * boveda). Si por algun motivo no hay partidas cargadas en la coleccion, se registra una fila
     * sin partida para no perder trazabilidad del cambio de estatus.
     */
    private void registrarDetalleEnVenta(PaseAlmoneda bitacora, Contrato contrato) {
        if (contrato.getPartidas() == null || contrato.getPartidas().isEmpty()) {
            PaseAlmonedaDetalle detalle = new PaseAlmonedaDetalle();
            detalle.setPase(bitacora);
            detalle.setContrato(contrato);
            detalle.setTipoCambio(TipoCambioPase.EN_VENTA);
            paseAlmonedaDetalleRepository.save(detalle);
            return;
        }
        for (PartidaContrato partida : contrato.getPartidas()) {
            PaseAlmonedaDetalle detalle = new PaseAlmonedaDetalle();
            detalle.setPase(bitacora);
            detalle.setContrato(contrato);
            detalle.setPartida(partida);
            detalle.setTipoCambio(TipoCambioPase.EN_VENTA);
            paseAlmonedaDetalleRepository.save(detalle);
        }
    }

    /**
     * Estatus operativo que las fechas (vencimiento + gracia + comercializacion) dictan para el
     * contrato. Delega en {@link EstatusContratoResolver#estatusPorFechas} para no duplicar reglas.
     */
    private EstatusOperativo estatusDerivadoPorFecha(Contrato contrato, LocalDate hoy) {
        int diasGracia = resolverDiasGracia(contrato);
        LocalDate comercializacion = contrato.getFechaComercializacion() != null
                ? contrato.getFechaComercializacion()
                : contrato.getFechaVencimiento().plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION);
        return EstatusContratoResolver.estatusPorFechas(
                contrato.getFechaVencimiento(), comercializacion, diasGracia, hoy);
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
     * estatus y las fechas de antes/despues. El estado nuevo se registra con EN_VENTA porque es
     * el que queda tras este movimiento.
     */
    private void registrarMovimientoPaseAVenta(Contrato contrato, Turno turno, EstatusContrato origen) {
        MovimientoContrato mov = new MovimientoContrato();
        mov.setContrato(contrato);
        mov.setTurno(turno);
        mov.setUsuario(turno.getUsuario());
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
        mov.setFecha(LocalDateTime.now());

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
