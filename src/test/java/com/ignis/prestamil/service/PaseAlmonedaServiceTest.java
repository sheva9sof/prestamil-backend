package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests del pase de almoneda (F11). "Hoy" es un reloj fijo y las fechas de los contratos se
 * construyen relativas a el, para que las transiciones dependan de donde cae el dia del pase
 * respecto a vencimiento, gracia y comercializacion (RN-04 / RN-08).
 */
@ExtendWith(MockitoExtension.class)
class PaseAlmonedaServiceTest {

    private static final int SUCURSAL = 1;
    private static final int DIAS_GRACIA = 2;
    private static final int DIAS_A_COMERCIALIZACION = 15;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 30, 0, 5);
    private static final LocalDate HOY = AHORA.toLocalDate();

    @Mock ContratoRepository contratoRepository;
    @Mock MovimientoContratoRepository movimientoContratoRepository;
    @Mock PaseAlmonedaRepository paseAlmonedaRepository;
    @Mock PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock SucursalService sucursalService;
    @Mock PlatformTransactionManager transactionManager;
    @Mock EntityManager entityManager;

    PaseAlmonedaService service;
    // Lo que devuelve la relectura con bloqueo: por defecto, el mismo contrato de la preseleccion
    private final Map<Long, Contrato> enBaseDeDatos = new HashMap<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new PaseAlmonedaService(contratoRepository, movimientoContratoRepository,
                paseAlmonedaRepository, paseAlmonedaDetalleRepository, plazoParametroRepository,
                sucursalRepository, sucursalService, entityManager, clock, transactionManager);
        // El servicio toma la bitacora recien guardada y la usa para FK en pase_almoneda_detalle
        lenient().when(paseAlmonedaRepository.save(any(PaseAlmoneda.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contratoRepository.findWithLockById(any()))
                .thenAnswer(inv -> Optional.ofNullable(enBaseDeDatos.get(inv.<Long>getArgument(0))));
    }

    private PartidaContrato partida(long id, int num, String descripcion) {
        PartidaContrato p = new PartidaContrato();
        p.setId(id);
        p.setNumPartida(num);
        p.setDescripcion(descripcion);
        p.setEstatus(EstatusPartida.OP);
        return p;
    }

    /**
     * Contrato cuyo vencimiento cae {@code diasDesdeVencimiento} dias antes de hoy.
     * 0 ⇒ hoy es el vencimiento (vigente); 1..gracia ⇒ en gracia; gracia+1..14 ⇒ vencido;
     * ≥ 15 ⇒ en venta.
     */
    private Contrato contrato(long id, EstatusContrato estatus, int diasDesdeVencimiento) {
        Contrato c = new Contrato();
        c.setId(id);
        c.setSucursalId(SUCURSAL);
        c.setEstatus(estatus);
        c.setFechaVencimiento(HOY.minusDays(diasDesdeVencimiento));
        c.setFechaComercializacion(c.getFechaVencimiento().plusDays(DIAS_A_COMERCIALIZACION));
        c.setFechaContrato(c.getFechaVencimiento().minusDays(28));
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setSaldoCapital(new BigDecimal("1000.00"));
        c.setNumRefrendos(0);
        c.setSnapDiasGraciaSancion(DIAS_GRACIA);
        c.setPartidas(new ArrayList<>());
        enBaseDeDatos.put(id, c);
        return c;
    }

    private void mockCandidatos(List<Contrato> vigentes, List<Contrato> vencidos) {
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VIGENTE))
                .thenReturn(new ArrayList<>(vigentes));
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VENCIDO))
                .thenReturn(new ArrayList<>(vencidos));
    }

    /** Como la BD: cada consulta devuelve los contratos segun su estatus en ese momento. */
    private void mockCandidatosConEstado(List<Contrato> contratos) {
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(any())).thenAnswer(inv ->
                new ArrayList<>(contratos.stream().filter(c -> c.getEstatus() == inv.getArgument(0)).toList()));
    }

    private PaseAlmoneda paseDel(LocalDate fecha) {
        PaseAlmoneda p = new PaseAlmoneda();
        p.setSucursalId(SUCURSAL);
        p.setFecha(fecha);
        return p;
    }

    private PaseAlmoneda ultimaBitacora() {
        ArgumentCaptor<PaseAlmoneda> bitacora = ArgumentCaptor.forClass(PaseAlmoneda.class);
        verify(paseAlmonedaRepository, atLeastOnce()).save(bitacora.capture());
        return bitacora.getAllValues().get(bitacora.getAllValues().size() - 1);
    }

    // =========================================================================
    // Idempotencia y bloqueo
    // =========================================================================

    @Test
    void ejecutar_diaYaCorrido_noHaceNada() {
        when(paseAlmonedaRepository.existsBySucursalIdAndFecha(SUCURSAL, HOY)).thenReturn(true);

        assertThat(service.ejecutar(SUCURSAL, HOY)).isFalse();

        verify(contratoRepository, never()).findByEstatusOrderByFechaVencimientoAsc(any());
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
        verify(paseAlmonedaRepository, never()).save(any());
    }

    @Test
    void ejecutar_bloqueaLaSucursalAntesDeRevisarSiYaCorrio() {
        when(paseAlmonedaRepository.existsBySucursalIdAndFecha(SUCURSAL, HOY)).thenReturn(true);

        service.ejecutar(SUCURSAL, HOY);

        // Con el bloqueo primero, una segunda ejecucion simultanea espera y luego ve el dia corrido
        InOrder orden = inOrder(sucursalRepository, paseAlmonedaRepository);
        orden.verify(sucursalRepository).findWithLockById(SUCURSAL);
        orden.verify(paseAlmonedaRepository).existsBySucursalIdAndFecha(SUCURSAL, HOY);
    }

    // =========================================================================
    // Transiciones
    // =========================================================================

    @Test
    void ejecutar_contratoEnGracia_noCambia() {
        // Hoy = vencimiento + 1 dia, con gracia 2: operativo = EN_GRACIA; no debe tocarse.
        Contrato enGracia = contrato(100L, EstatusContrato.VIGENTE, 1);
        mockCandidatos(List.of(enGracia), List.of());

        assertThat(service.ejecutar(SUCURSAL, HOY)).isTrue();

        assertThat(enGracia.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
        verify(paseAlmonedaDetalleRepository, never()).save(any());
        PaseAlmoneda ultima = ultimaBitacora();
        assertThat(ultima.getFecha()).isEqualTo(HOY);
        assertThat(ultima.getContratosAVencido()).isZero();
        assertThat(ultima.getContratosAVenta()).isZero();
        assertThat(ultima.getMontoPasadoAVenta()).isEqualByComparingTo("0");
    }

    @Test
    void ejecutar_contratoConGraciaVencida_pasaAVencidoConVencimientoYSaldoDelDia() {
        // Hoy = vencimiento + 3 dias, gracia 2: fuera de gracia pero antes de comercializacion.
        Contrato tarde = contrato(101L, EstatusContrato.VIGENTE, 3);
        tarde.setSaldoCapital(new BigDecimal("850.00"));
        mockCandidatos(List.of(tarde), List.of());

        service.ejecutar(SUCURSAL, HOY);

        assertThat(tarde.getEstatus()).isEqualTo(EstatusContrato.VENCIDO);
        verify(contratoRepository).save(tarde);
        verify(movimientoContratoRepository, never()).save(any());
        ArgumentCaptor<PaseAlmonedaDetalle> captor = ArgumentCaptor.forClass(PaseAlmonedaDetalle.class);
        verify(paseAlmonedaDetalleRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoCambio()).isEqualTo(TipoCambioPase.VENCIDO);
        assertThat(captor.getValue().getFechaVencimiento()).isEqualTo(HOY.minusDays(3));
        assertThat(captor.getValue().getSaldoCapital()).isEqualByComparingTo("850.00");

        PaseAlmoneda ultima = ultimaBitacora();
        assertThat(ultima.getContratosAVencido()).isEqualTo(1);
        assertThat(ultima.getContratosAVenta()).isZero();
    }

    @Test
    void ejecutar_contratoEnFechaDeComercializacion_pasaAEnVentaConMovimientoPVAutomatico() {
        // Hoy = vencimiento + 15 (= fecha_comercializacion). Debe mover a EN_VENTA.
        Contrato aVenta = contrato(102L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        aVenta.setMontoPrestamo(new BigDecimal("2050.00"));
        aVenta.getPartidas().add(partida(500L, 1, "Anillo oro 14k"));
        mockCandidatos(List.of(), List.of(aVenta));

        service.ejecutar(SUCURSAL, HOY);

        assertThat(aVenta.getEstatus()).isEqualTo(EstatusContrato.EN_VENTA);
        verify(contratoRepository).save(aVenta);

        ArgumentCaptor<MovimientoContrato> movCaptor = ArgumentCaptor.forClass(MovimientoContrato.class);
        verify(movimientoContratoRepository).save(movCaptor.capture());
        MovimientoContrato mov = movCaptor.getValue();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.PV);
        assertThat(mov.getContrato()).isEqualTo(aVenta);
        // Automatico: sin turno ni usuario (changeset 078); pase de hoy = hora real del reloj
        assertThat(mov.getTurno()).isNull();
        assertThat(mov.getUsuario()).isNull();
        assertThat(mov.getFecha()).isEqualTo(AHORA);
        assertThat(mov.getMonto()).isEqualByComparingTo("0");
        assertThat(mov.getEstatusAnterior()).isEqualTo(EstatusContrato.VENCIDO);
        assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.EN_VENTA);
        assertThat(mov.getCancelado()).isFalse();

        PaseAlmoneda ultima = ultimaBitacora();
        assertThat(ultima.getContratosAVenta()).isEqualTo(1);
        assertThat(ultima.getMontoPasadoAVenta()).isEqualByComparingTo("2050.00");
    }

    @Test
    void ejecutar_vigenteQueSaltaAVenta_tambienQuedaEnCarteraVencida() {
        // Primer pase de la sucursal con un contrato muy atrasado: VIGENTE y ya en comercializacion.
        Contrato atrasado = contrato(103L, EstatusContrato.VIGENTE, DIAS_A_COMERCIALIZACION + 5);
        atrasado.getPartidas().add(partida(600L, 1, "Cadena oro 10k"));
        mockCandidatos(List.of(atrasado), List.of());

        service.ejecutar(SUCURSAL, HOY);

        assertThat(atrasado.getEstatus()).isEqualTo(EstatusContrato.EN_VENTA);
        ArgumentCaptor<PaseAlmonedaDetalle> captor = ArgumentCaptor.forClass(PaseAlmonedaDetalle.class);
        verify(paseAlmonedaDetalleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaseAlmonedaDetalle::getTipoCambio)
                .containsExactly(TipoCambioPase.VENCIDO, TipoCambioPase.EN_VENTA);
        PaseAlmoneda ultima = ultimaBitacora();
        assertThat(ultima.getContratosAVencido()).isEqualTo(1);
        assertThat(ultima.getContratosAVenta()).isEqualTo(1);
    }

    @Test
    void ejecutar_contratoFiniquitado_noSeToca() {
        // Un FINIQUITADO cuyas fechas lo pondrian en venta si fuera operable: se queda igual.
        Contrato finiquitado = contrato(104L, EstatusContrato.FINIQUITADO, DIAS_A_COMERCIALIZACION);
        // El servicio solo consulta los estatus VIGENTE y VENCIDO; FINIQUITADO ni siquiera entra
        // en la busqueda. Verificamos que la consulta no lo incluye y que nada se escribe.
        mockCandidatos(List.of(), List.of());

        service.ejecutar(SUCURSAL, HOY);

        assertThat(finiquitado.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
        verify(contratoRepository, never()).findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.FINIQUITADO);
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
    }

    // =========================================================================
    // Relectura con bloqueo (C-10)
    // =========================================================================

    @Test
    void ejecutar_releeBloqueadoAntesDeCambiar_ySoloLosQueLasFechasMandanMover() {
        Contrato enGracia = contrato(110L, EstatusContrato.VIGENTE, 1);
        Contrato aVenta = contrato(111L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        mockCandidatos(List.of(enGracia), List.of(aVenta));

        service.ejecutar(SUCURSAL, HOY);

        // La preseleccion se saca del contexto de persistencia antes de releer; el que no cambia ni se relee
        InOrder orden = inOrder(entityManager, contratoRepository);
        orden.verify(entityManager).detach(aVenta);
        orden.verify(contratoRepository).findWithLockById(111L);
        orden.verify(contratoRepository).save(aVenta);
        verify(contratoRepository, never()).findWithLockById(110L);
    }

    @Test
    void ejecutar_contratoRefrendadoDuranteElPase_seOmiteSinPvNiDetalle() {
        // La preseleccion lo ve VENCIDO y en fecha de venta; al releerlo ya lo refrendaron
        Contrato preseleccion = contrato(112L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        Contrato refrendado = contrato(112L, EstatusContrato.VIGENTE, -28);
        mockCandidatos(List.of(), List.of(preseleccion));

        service.ejecutar(SUCURSAL, HOY);

        assertThat(refrendado.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
        verify(paseAlmonedaDetalleRepository, never()).save(any());
        assertThat(ultimaBitacora().getContratosAVenta()).isZero();
    }

    // =========================================================================
    // Detalle del pase (C-11)
    // =========================================================================

    @Test
    void ejecutar_dosVencidosYUnaVenta_registraExactamenteTresFilasDetalle() {
        // Dos VIGENTE con gracia expirada y uno VENCIDO en fecha de comercializacion con 1 partida.
        Contrato v1 = contrato(201L, EstatusContrato.VIGENTE, 3);
        Contrato v2 = contrato(202L, EstatusContrato.VIGENTE, 4);
        Contrato aVenta = contrato(203L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        aVenta.getPartidas().add(partida(700L, 1, "Reloj acero"));
        mockCandidatos(List.of(v1, v2), List.of(aVenta));

        service.ejecutar(SUCURSAL, HOY);

        ArgumentCaptor<PaseAlmonedaDetalle> captor = ArgumentCaptor.forClass(PaseAlmonedaDetalle.class);
        verify(paseAlmonedaDetalleRepository, times(3)).save(captor.capture());
        List<PaseAlmonedaDetalle> filas = captor.getAllValues();

        assertThat(filas).filteredOn(d -> d.getTipoCambio() == TipoCambioPase.VENCIDO)
                .extracting(d -> d.getContrato().getId())
                .containsExactlyInAnyOrder(201L, 202L);
        assertThat(filas).filteredOn(d -> d.getTipoCambio() == TipoCambioPase.VENCIDO)
                .allMatch(d -> d.getPartida() == null, "VENCIDO sin partida");

        assertThat(filas).filteredOn(d -> d.getTipoCambio() == TipoCambioPase.EN_VENTA)
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getContrato().getId()).isEqualTo(203L);
                    assertThat(d.getPartida()).isNotNull();
                    assertThat(d.getPartida().getId()).isEqualTo(700L);
                });

        PaseAlmoneda ultima = ultimaBitacora();
        assertThat(ultima.getContratosAVencido()).isEqualTo(2);
        assertThat(ultima.getContratosAVenta()).isEqualTo(1);
    }

    @Test
    void ejecutar_paseAVenta_soloListaPartidasEnOperacion() {
        // Solo las partidas OP siguen en boveda; las finiquitadas o canceladas no se sacan.
        Contrato aVenta = contrato(301L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        PartidaContrato enBoveda = partida(801L, 1, "Anillo oro 14k");
        PartidaContrato entregada = partida(802L, 2, "Pulsera oro 10k");
        entregada.setEstatus(EstatusPartida.FIN);
        PartidaContrato cancelada = partida(803L, 3, "Arete oro 14k");
        cancelada.setEstatus(EstatusPartida.CAN);
        aVenta.getPartidas().addAll(List.of(enBoveda, entregada, cancelada));
        mockCandidatos(List.of(), List.of(aVenta));

        service.ejecutar(SUCURSAL, HOY);

        ArgumentCaptor<PaseAlmonedaDetalle> captor = ArgumentCaptor.forClass(PaseAlmonedaDetalle.class);
        verify(paseAlmonedaDetalleRepository).save(captor.capture());
        assertThat(captor.getValue().getPartida()).isSameAs(enBoveda);
    }

    // =========================================================================
    // Puesta al dia (ejecutarPendientes)
    // =========================================================================

    @Test
    void ejecutarPendientes_paseConVeinteDiasSinCorrer_pasaPorCarteraVencidaEnSuDiaAntesDeVenta() {
        // Ultimo pase hace 20 dias. El contrato vencio hace 18 dias con gracia 2: debio quedar
        // vencido hace 15 dias y pasar a venta hace 3 (vencimiento + 15).
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL))
                .thenReturn(Optional.of(paseDel(HOY.minusDays(20))));
        Contrato atrasado = contrato(401L, EstatusContrato.VIGENTE, 18);
        atrasado.getPartidas().add(partida(901L, 1, "Esclava oro 14k"));
        mockCandidatosConEstado(List.of(atrasado));

        int corridos = service.ejecutarPendientes(SUCURSAL);

        // Un pase por dia faltante, en orden: de hace 19 dias hasta hoy
        assertThat(corridos).isEqualTo(20);
        ArgumentCaptor<LocalDate> dias = ArgumentCaptor.forClass(LocalDate.class);
        verify(paseAlmonedaRepository, times(20)).existsBySucursalIdAndFecha(eq(SUCURSAL), dias.capture());
        assertThat(dias.getAllValues()).first().isEqualTo(HOY.minusDays(19));
        assertThat(dias.getAllValues()).last().isEqualTo(HOY);
        assertThat(dias.getAllValues()).isSorted().doesNotHaveDuplicates();

        // Cartera vencida en su dia (hace 15) y pase a venta en el suyo (hace 3), en ese orden
        ArgumentCaptor<PaseAlmonedaDetalle> captor = ArgumentCaptor.forClass(PaseAlmonedaDetalle.class);
        verify(paseAlmonedaDetalleRepository, times(2)).save(captor.capture());
        PaseAlmonedaDetalle vencido = captor.getAllValues().get(0);
        PaseAlmonedaDetalle enVenta = captor.getAllValues().get(1);
        assertThat(vencido.getTipoCambio()).isEqualTo(TipoCambioPase.VENCIDO);
        assertThat(vencido.getPase().getFecha()).isEqualTo(HOY.minusDays(15));
        assertThat(enVenta.getTipoCambio()).isEqualTo(TipoCambioPase.EN_VENTA);
        assertThat(enVenta.getPase().getFecha()).isEqualTo(HOY.minusDays(3));

        // El PV queda fechado el dia en que paso a venta, no hoy
        ArgumentCaptor<MovimientoContrato> mov = ArgumentCaptor.forClass(MovimientoContrato.class);
        verify(movimientoContratoRepository).save(mov.capture());
        assertThat(mov.getValue().getEstatusAnterior()).isEqualTo(EstatusContrato.VENCIDO);
        assertThat(mov.getValue().getFecha()).isEqualTo(HOY.minusDays(3).atStartOfDay());
        assertThat(atrasado.getEstatus()).isEqualTo(EstatusContrato.EN_VENTA);
    }

    @Test
    void ejecutarPendientes_sucursalSinPases_soloCorreHoy() {
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL)).thenReturn(Optional.empty());
        mockCandidatos(List.of(), List.of());

        assertThat(service.ejecutarPendientes(SUCURSAL)).isEqualTo(1);

        verify(paseAlmonedaRepository).existsBySucursalIdAndFecha(SUCURSAL, HOY);
    }

    @Test
    void ejecutarPendientes_alDia_noCorreNada() {
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL))
                .thenReturn(Optional.of(paseDel(HOY)));

        assertThat(service.ejecutarPendientes(SUCURSAL)).isZero();

        verify(paseAlmonedaRepository, never()).existsBySucursalIdAndFecha(any(), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void ejecutarPendientes_otroProcesoYaCorrioElDia_noLoRepite() {
        // La otra ejecucion gano el bloqueo y ya registro ayer y hoy
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL))
                .thenReturn(Optional.of(paseDel(HOY.minusDays(2))));
        when(paseAlmonedaRepository.existsBySucursalIdAndFecha(eq(SUCURSAL), any())).thenReturn(true);

        assertThat(service.ejecutarPendientes(SUCURSAL)).isZero();

        verify(paseAlmonedaRepository, never()).save(any());
        verify(contratoRepository, never()).save(any());
    }

    @Test
    void ejecutarPendientes_unDiaFalla_seDetieneSinSaltarDiasNiLanzar() {
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL))
                .thenReturn(Optional.of(paseDel(HOY.minusDays(3))));
        when(paseAlmonedaRepository.existsBySucursalIdAndFecha(SUCURSAL, HOY.minusDays(2))).thenReturn(false);
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VIGENTE))
                .thenThrow(new IllegalStateException("BD no disponible"));

        assertThat(service.ejecutarPendientes(SUCURSAL)).isZero();

        // No intenta ayer ni hoy: el siguiente disparador reintenta desde el dia que fallo
        verify(paseAlmonedaRepository, never()).existsBySucursalIdAndFecha(SUCURSAL, HOY.minusDays(1));
        verify(paseAlmonedaRepository, never()).existsBySucursalIdAndFecha(SUCURSAL, HOY);
        verify(transactionManager).rollback(any());
    }

    @Test
    void ejecutarPendientes_sinSucursalConfigurada_noHaceNada() {
        when(sucursalService.findUnique()).thenReturn(Optional.empty());

        assertThat(service.ejecutarPendientes()).isZero();

        verifyNoInteractions(paseAlmonedaRepository);
    }

    @Test
    void ejecutarPendientes_usaLaSucursalConfigurada() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        when(sucursalService.findUnique()).thenReturn(Optional.of(sucursal));
        when(paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL))
                .thenReturn(Optional.of(paseDel(HOY)));

        service.ejecutarPendientes();

        verify(paseAlmonedaRepository).findFirstBySucursalIdOrderByFechaDesc(SUCURSAL);
    }
}
