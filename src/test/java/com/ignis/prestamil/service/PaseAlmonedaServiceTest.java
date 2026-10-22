package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests del pase de almoneda (F11). Las fechas de los contratos se construyen relativas a
 * {@code LocalDate.now()} para que las transiciones dependan de donde cae "hoy" respecto a
 * vencimiento, gracia y comercializacion (RN-04 / RN-08).
 */
@ExtendWith(MockitoExtension.class)
class PaseAlmonedaServiceTest {

    private static final int SUCURSAL = 1;
    private static final int DIAS_GRACIA = 2;
    private static final int DIAS_A_COMERCIALIZACION = 15;

    @Mock
    ContratoRepository contratoRepository;

    @Mock
    MovimientoContratoRepository movimientoContratoRepository;

    @Mock
    PaseAlmonedaRepository paseAlmonedaRepository;

    @Mock
    PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository;

    @Mock
    PlazoParametroRepository plazoParametroRepository;

    @InjectMocks
    PaseAlmonedaService service;

    private Turno turno;

    @BeforeEach
    void setUp() {
        Usuario u = new Usuario();
        u.setId(10);
        u.setNombreUsuario("admin");
        turno = new Turno();
        turno.setId(1);
        turno.setUsuario(u);
        // El servicio toma la bitacora recien guardada y la usa para FK en pase_almoneda_detalle
        lenient().when(paseAlmonedaRepository.save(any(PaseAlmoneda.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Construye un contrato con fechas que, respecto a hoy, caen en el estado operativo deseado.
     * {@code diasDesdeVencimiento = 0} ⇒ hoy es el vencimiento (vigente); 1..gracia ⇒ en gracia;
     * gracia+1..dias_a_comercializacion-1 ⇒ vencido; ≥ dias_a_comercializacion ⇒ en venta.
     */
    private PartidaContrato partida(long id, int num, String descripcion) {
        PartidaContrato p = new PartidaContrato();
        p.setId(id);
        p.setNumPartida(num);
        p.setDescripcion(descripcion);
        return p;
    }

    private Contrato contrato(long id, EstatusContrato estatus, int diasDesdeVencimiento) {
        LocalDate hoy = LocalDate.now();
        Contrato c = new Contrato();
        c.setId(id);
        c.setSucursalId(SUCURSAL);
        c.setEstatus(estatus);
        c.setFechaVencimiento(hoy.minusDays(diasDesdeVencimiento));
        c.setFechaComercializacion(c.getFechaVencimiento().plusDays(DIAS_A_COMERCIALIZACION));
        c.setFechaContrato(c.getFechaVencimiento().minusDays(28));
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setSaldoCapital(new BigDecimal("1000.00"));
        c.setNumRefrendos(0);
        c.setSnapDiasGraciaSancion(DIAS_GRACIA);
        c.setPartidas(new ArrayList<>());
        return c;
    }

    private void mockCandidatos(List<Contrato> vigentes, List<Contrato> vencidos) {
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VIGENTE))
                .thenReturn(new ArrayList<>(vigentes));
        when(contratoRepository.findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.VENCIDO))
                .thenReturn(new ArrayList<>(vencidos));
    }

    // =========================================================================
    // Idempotencia
    // =========================================================================

    @Test
    void ejecutar_segundaCorridaDelDia_noHaceNada() {
        when(paseAlmonedaRepository.existsBySucursalIdAndFecha(SUCURSAL, LocalDate.now()))
                .thenReturn(true);

        service.ejecutar(SUCURSAL, turno);

        verify(contratoRepository, never()).findByEstatusOrderByFechaVencimientoAsc(any());
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
        verify(paseAlmonedaRepository, never()).save(any());
    }

    // =========================================================================
    // Transiciones
    // =========================================================================

    @Test
    void ejecutar_contratoEnGracia_noCambia() {
        // Hoy = vencimiento + 1 dia, con gracia 2: operativo = EN_GRACIA; no debe tocarse.
        Contrato enGracia = contrato(100L, EstatusContrato.VIGENTE, 1);
        mockCandidatos(List.of(enGracia), List.of());

        service.ejecutar(SUCURSAL, turno);

        assertThat(enGracia.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
        verify(paseAlmonedaDetalleRepository, never()).save(any());
        ArgumentCaptor<PaseAlmoneda> bitacora = ArgumentCaptor.forClass(PaseAlmoneda.class);
        verify(paseAlmonedaRepository, atLeastOnce()).save(bitacora.capture());
        PaseAlmoneda ultima = bitacora.getAllValues().get(bitacora.getAllValues().size() - 1);
        assertThat(ultima.getContratosAVencido()).isZero();
        assertThat(ultima.getContratosAVenta()).isZero();
        assertThat(ultima.getMontoPasadoAVenta()).isEqualByComparingTo("0");
    }

    @Test
    void ejecutar_contratoConGraciaVencida_pasaAVencido() {
        // Hoy = vencimiento + 3 dias, gracia 2: fuera de gracia pero antes de comercializacion.
        Contrato tarde = contrato(101L, EstatusContrato.VIGENTE, 3);
        mockCandidatos(List.of(tarde), List.of());

        service.ejecutar(SUCURSAL, turno);

        assertThat(tarde.getEstatus()).isEqualTo(EstatusContrato.VENCIDO);
        verify(contratoRepository).save(tarde);
        verify(movimientoContratoRepository, never()).save(any());

        ArgumentCaptor<PaseAlmoneda> bitacora = ArgumentCaptor.forClass(PaseAlmoneda.class);
        verify(paseAlmonedaRepository, atLeastOnce()).save(bitacora.capture());
        PaseAlmoneda ultima = bitacora.getAllValues().get(bitacora.getAllValues().size() - 1);
        assertThat(ultima.getContratosAVencido()).isEqualTo(1);
        assertThat(ultima.getContratosAVenta()).isZero();
    }

    @Test
    void ejecutar_contratoEnFechaDeComercializacion_pasaAEnVentaConMovimientoPV() {
        // Hoy = vencimiento + 15 (= fecha_comercializacion). Debe mover a EN_VENTA.
        Contrato aVenta = contrato(102L, EstatusContrato.VENCIDO, DIAS_A_COMERCIALIZACION);
        aVenta.setMontoPrestamo(new BigDecimal("2050.00"));
        aVenta.getPartidas().add(partida(500L, 1, "Anillo oro 14k"));
        mockCandidatos(List.of(), List.of(aVenta));

        service.ejecutar(SUCURSAL, turno);

        assertThat(aVenta.getEstatus()).isEqualTo(EstatusContrato.EN_VENTA);
        verify(contratoRepository).save(aVenta);

        ArgumentCaptor<MovimientoContrato> movCaptor = ArgumentCaptor.forClass(MovimientoContrato.class);
        verify(movimientoContratoRepository).save(movCaptor.capture());
        MovimientoContrato mov = movCaptor.getValue();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.PV);
        assertThat(mov.getContrato()).isEqualTo(aVenta);
        assertThat(mov.getTurno()).isEqualTo(turno);
        assertThat(mov.getUsuario()).isEqualTo(turno.getUsuario());
        assertThat(mov.getMonto()).isEqualByComparingTo("0");
        assertThat(mov.getEstatusAnterior()).isEqualTo(EstatusContrato.VENCIDO);
        assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.EN_VENTA);
        assertThat(mov.getCancelado()).isFalse();

        // Bitacora guardada dos veces: antes de iterar (contadores en cero) y al final con totales
        ArgumentCaptor<PaseAlmoneda> bitacora = ArgumentCaptor.forClass(PaseAlmoneda.class);
        verify(paseAlmonedaRepository, atLeastOnce()).save(bitacora.capture());
        PaseAlmoneda ultima = bitacora.getAllValues().get(bitacora.getAllValues().size() - 1);
        assertThat(ultima.getContratosAVenta()).isEqualTo(1);
        assertThat(ultima.getMontoPasadoAVenta()).isEqualByComparingTo("2050.00");
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

        service.ejecutar(SUCURSAL, turno);

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

        ArgumentCaptor<PaseAlmoneda> bitacora = ArgumentCaptor.forClass(PaseAlmoneda.class);
        verify(paseAlmonedaRepository, atLeastOnce()).save(bitacora.capture());
        PaseAlmoneda ultima = bitacora.getAllValues().get(bitacora.getAllValues().size() - 1);
        assertThat(ultima.getContratosAVencido()).isEqualTo(2);
        assertThat(ultima.getContratosAVenta()).isEqualTo(1);
    }

    @Test
    void ejecutar_contratoFiniquitado_noSeToca() {
        // Un FINIQUITADO cuyas fechas lo pondrian en venta si fuera operable: se queda igual.
        Contrato finiquitado = contrato(103L, EstatusContrato.FINIQUITADO, DIAS_A_COMERCIALIZACION);
        // El servicio solo consulta los estatus VIGENTE y VENCIDO; FINIQUITADO ni siquiera entra
        // en la busqueda. Verificamos que la consulta no lo incluye y que nada se escribe.
        mockCandidatos(List.of(), List.of());

        service.ejecutar(SUCURSAL, turno);

        assertThat(finiquitado.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
        verify(contratoRepository, never()).findByEstatusOrderByFechaVencimientoAsc(EstatusContrato.FINIQUITADO);
        verify(contratoRepository, never()).save(any());
        verify(movimientoContratoRepository, never()).save(any());
    }
}
