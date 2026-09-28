package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests de MovimientoContratoService.refrendar tras la Pasada 2. Cubre:
 *   - Refrendo dentro de gracia: sancion=0, IVA sobre solo interes.
 *   - Refrendo extemporaneo: sancion>0, IVA sobre (interes + almacen + gastos + sancion).
 *   - El snapshot del contrato (changeset 026) tiene prioridad sobre la config vigente.
 *   - Consistencia con la fila extemporanea del PDF para plazo QUINCENAL (diasPorPeriodo=15,
 *     el caso donde ambos motores divergian antes).
 *   - Regla del maximo de refrendos preservada.
 *
 * Este servicio NO tenia tests antes de Pasada 2 (documentado en STATE.md).
 */
@ExtendWith(MockitoExtension.class)
class MovimientoContratoServiceTest {

    @Mock MovimientoContratoRepository movimientoRepository;
    @Mock ContratoRepository contratoRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock TurnoRepository turnoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;

    // Motor REAL (no mock): el punto de estos tests es verificar que refrendar consume el motor
    // correctamente, no re-testear el motor (eso ya se hace en CalculoContratoServiceTest).
    CalculoContratoService calculoContratoService;
    MovimientoContratoService service;

    @BeforeEach
    void setUp() {
        calculoContratoService = new CalculoContratoService(parametrosSistemaCache);
        service = new MovimientoContratoService(
                movimientoRepository, contratoRepository, plazoParametroRepository,
                turnoRepository, usuarioRepository, calculoContratoService);

        // Turno y usuario siempre resolubles
        Turno turno = new Turno();
        turno.setId(1);
        lenient().when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turno));
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        lenient().when(usuarioRepository.findByNombreUsuario("cajero1")).thenReturn(Optional.of(usuario));

        // IVA por defecto para tests sin snapshot: 16%
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

        // Los movimientos se guardan con el id que llega
        lenient().when(movimientoRepository.save(any(MovimientoContrato.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * Contrato de referencia: prestamo 1000, vencimiento hace {diasAtraso} dias, plazo semanal
     * (7 dias/periodo). Sin snapshot -> el motor usa el PlazoParametro vigente.
     */
    private Contrato contratoRef(int diasAtraso, int diasPorPeriodo) {
        Contrato c = new Contrato();
        c.setId(42L);
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setSaldoCapital(new BigDecimal("1000.00"));
        c.setNumRefrendos(0);
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setSucursalId(1);
        c.setFechaVencimiento(LocalDate.now().minusDays(diasAtraso));
        c.setFechaContrato(c.getFechaVencimiento().minusDays(4L * diasPorPeriodo));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(diasPorPeriodo);
        c.setPlazo(plazo);

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        PartidaContrato p = new PartidaContrato();
        p.setTipoPrenda(alhaja);
        List<PartidaContrato> ps = new ArrayList<>();
        ps.add(p);
        c.setPartidas(ps);
        return c;
    }

    /** PlazoParametro con los 7 campos que el motor consume (config vigente). */
    private PlazoParametro paramVigente() {
        PlazoParametro pp = new PlazoParametro();
        pp.setPorcInteres(new BigDecimal("3.0000"));
        pp.setPorcAlmacen(new BigDecimal("2.0000"));
        pp.setPorcGastosAdmin(new BigDecimal("1.0000"));
        pp.setPorcSancionSemanal(new BigDecimal("2.0000"));
        pp.setDiasGraciaSinInteres(2);
        pp.setAplicarSancionPorPeriodo(true);
        return pp;
    }

    private RefrendoRequest refrendoRequest(BigDecimal abono) {
        RefrendoRequest r = new RefrendoRequest();
        r.setIdContrato(42L);
        r.setAbonoCapital(abono);
        return r;
    }

    private MovimientoContrato capturarMovimiento() {
        ArgumentCaptor<MovimientoContrato> captor = ArgumentCaptor.forClass(MovimientoContrato.class);
        org.mockito.Mockito.verify(movimientoRepository).save(captor.capture());
        return captor.getValue();
    }

    // =========================================================================
    // A. Refrendo dentro de gracia -> sin sancion, IVA solo sobre interes
    // =========================================================================

    @Test
    void refrendo_dentroDeGracia_noAplicaSancion_ivaSoloSobreInteres() {
        // Given: 2 dias de atraso, gracia 2 -> sancion = 0
        Contrato c = contratoRef(2, 7);
        PlazoParametro pp = paramVigente();
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        // When
        MovimientoResponse resp = service.refrendar(refrendoRequest(BigDecimal.ZERO), "cajero1");

        // Then
        MovimientoContrato mov = capturarMovimiento();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RF);
        assertThat(mov.getSancion()).isEqualByComparingTo("0.00");
        assertThat(mov.getSemanasVencidas()).isZero();

        // interes total (interes + almacen + gastos) = 1000 * (3+2+1)/100 = 60.00
        assertThat(mov.getInteres()).isEqualByComparingTo("60.00");

        // baseIva = 60 (sin sancion); IVA = 60 * 16/100 = 9.60
        // total = 60 + 9.60 + abono(0) = 69.60
        assertThat(mov.getMonto()).isEqualByComparingTo("69.60");
        assertThat(resp.getSancion()).isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // B. Refrendo extemporaneo -> sancion>0, IVA sobre (interes + sancion) -- CAMBIO DE COBRO
    // =========================================================================

    @Test
    void refrendo_extemporaneo10DiasAtraso_ivaAplicaSobreInteresMasSancion() {
        // Given: 10 dias de atraso, gracia 2 -> diasAtraso=8 -> ceil(8/7)=2 semanas
        Contrato c = contratoRef(10, 7);
        PlazoParametro pp = paramVigente();
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        // When
        MovimientoResponse resp = service.refrendar(refrendoRequest(BigDecimal.ZERO), "cajero1");

        // Then
        MovimientoContrato mov = capturarMovimiento();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RX);
        assertThat(mov.getSemanasVencidas()).isEqualTo(2);

        // sancion = 1000 * 2/100 * 2 = 40.00
        assertThat(mov.getSancion()).isEqualByComparingTo("40.00");
        // interes total = 60.00
        assertThat(mov.getInteres()).isEqualByComparingTo("60.00");

        // baseIva = 60 + 40 = 100; IVA = 100 * 16/100 = 16.00 (aqui esta el CAMBIO DE COBRO: antes
        // refrendar cobraba 100 sin IVA; ahora cobra 116.00)
        // total = 60 + 40 + 16 + abono(0) = 116.00
        assertThat(mov.getMonto()).isEqualByComparingTo("116.00");
        assertThat(resp.getMonto()).isEqualByComparingTo("116.00");
    }

    // =========================================================================
    // C. El abono NO lleva IVA
    // =========================================================================

    @Test
    void refrendo_conAbonoCapital_elAbonoNoLlevaIva() {
        Contrato c = contratoRef(0, 7); // sin atraso
        PlazoParametro pp = paramVigente();
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        // When: abono de 500
        service.refrendar(refrendoRequest(new BigDecimal("500.00")), "cajero1");

        // Then: total = interes(60) + iva(9.60) + abono(500) = 569.60. El abono queda intacto.
        MovimientoContrato mov = capturarMovimiento();
        assertThat(mov.getMonto()).isEqualByComparingTo("569.60");
        assertThat(mov.getAbonoCapital()).isEqualByComparingTo("500.00");
        assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RC);
    }

    // =========================================================================
    // C.2 Changeset 027: el movimiento guarda el estado antes/despues y el contrato
    //     mantiene saldo, fecha de contrato y comercializacion sincronizados
    // =========================================================================

    @Test
    void refrendo_registraEstadoAnteriorYNuevo_yActualizaSaldoYFechas() {
        Contrato c = contratoRef(0, 7);
        LocalDate fechaContratoAntes = c.getFechaContrato();
        LocalDate vencAntes = c.getFechaVencimiento();
        PlazoParametro pp = paramVigente();
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        service.refrendar(refrendoRequest(new BigDecimal("300.00")), "cajero1");

        MovimientoContrato mov = capturarMovimiento();
        assertThat(mov.getSaldoAnterior()).isEqualByComparingTo("1000.00");
        assertThat(mov.getSaldoNuevo()).isEqualByComparingTo("700.00");
        assertThat(mov.getFechaContratoAnterior()).isEqualTo(fechaContratoAntes);
        assertThat(mov.getFechaVencAnterior()).isEqualTo(vencAntes);
        assertThat(mov.getFechaContratoNueva()).isEqualTo(fechaContratoAntes.plusDays(7));
        assertThat(mov.getFechaVencNueva()).isEqualTo(vencAntes.plusDays(7));
        assertThat(mov.getEstatusAnterior()).isEqualTo(EstatusContrato.VIGENTE);
        assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.VIGENTE);
        assertThat(mov.getNumRefrendosAnterior()).isZero();
        // IVA del desglose (60 * 16%) guardado aparte
        assertThat(mov.getIva()).isEqualByComparingTo("9.60");

        assertThat(c.getSaldoCapital()).isEqualByComparingTo("700.00");
        assertThat(c.getFechaContrato()).isEqualTo(fechaContratoAntes.plusDays(7));
        assertThat(c.getFechaComercializacion()).isEqualTo(c.getFechaVencimiento().plusDays(15));
        assertThat(c.getNumRefrendos()).isEqualTo(1);
    }

    // =========================================================================
    // D. Snapshot del contrato tiene prioridad sobre config vigente
    // =========================================================================

    @Test
    void refrendo_conSnapshot_usaValoresDelSnapshotNoLaConfigVigente() {
        // Given: contrato con snapshot al 2%/2dias/16IVA, pero config vigente cambio a 999% / 0 gracia
        Contrato c = contratoRef(10, 7);
        c.setSnapPorcInteres(new BigDecimal("3.0000"));
        c.setSnapPorcAlmacen(new BigDecimal("2.0000"));
        c.setSnapPorcGastosAdmin(new BigDecimal("1.0000"));
        c.setSnapPorcSancionSemanal(new BigDecimal("2.0000"));
        c.setSnapDiasGraciaSancion(2);
        c.setSnapAplicarSancionPeriodo(true);
        c.setSnapIvaPorcentaje(new BigDecimal("16.00"));

        PlazoParametro ppVigenteMuyDistinto = new PlazoParametro();
        ppVigenteMuyDistinto.setPorcInteres(new BigDecimal("999.0000"));
        ppVigenteMuyDistinto.setPorcAlmacen(new BigDecimal("999.0000"));
        ppVigenteMuyDistinto.setPorcGastosAdmin(new BigDecimal("999.0000"));
        ppVigenteMuyDistinto.setPorcSancionSemanal(new BigDecimal("999.0000"));
        ppVigenteMuyDistinto.setDiasGraciaSinInteres(0);
        ppVigenteMuyDistinto.setAplicarSancionPorPeriodo(false);
        // El cache mockeado sigue devolviendo 16 (default del setUp), pero como el snapshot tambien es
        // 16 no hay ruido; lo importante es que se use el snapshot.

        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(ppVigenteMuyDistinto));

        // When
        service.refrendar(refrendoRequest(BigDecimal.ZERO), "cajero1");

        // Then: usa snapshot -> mismo resultado que test B (total 116.00) pese a la config disparatada
        MovimientoContrato mov = capturarMovimiento();
        assertThat(mov.getSancion()).isEqualByComparingTo("40.00");
        assertThat(mov.getMonto()).isEqualByComparingTo("116.00");
    }

    // =========================================================================
    // E. Consistencia refrendar <-> PDF en plazo QUINCENAL (diasPorPeriodo=15)
    //    Antes divergian aqui: PDF usaba k como multiplicador de semanas (=1 y 2), refrendar
    //    usaba ceil(dias/7). Con quincenal, k=1 (15 dias) daba 1 semana en PDF, pero refrendar
    //    daba ceil(15/7)=3. Ahora ambos usan la misma formula via el motor.
    // =========================================================================

    @Test
    void consistenciaRefrendarVsMotor_plazoQuincenal_15DiasAtraso() {
        Contrato c = contratoRef(15, 15); // quincenal, 15 dias de atraso
        PlazoParametro pp = paramVigente();
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        // Snapshot del desglose ANTES de refrendar (refrendar muta fechaVencimiento sumandole
        // diasPorPeriodo, lo que anula el atraso y falsearia la comparacion posterior).
        LocalDate fechaPago = LocalDate.now();
        var expected = calculoContratoService.calcularCobroPeriodo(c, pp, fechaPago, 1);

        service.refrendar(refrendoRequest(BigDecimal.ZERO), "cajero1");

        MovimientoContrato mov = capturarMovimiento();
        // 15 dias atraso - 2 gracia = 13 dias -> ceil(13/7) = 2 semanas
        assertThat(mov.getSemanasVencidas()).isEqualTo(2);
        assertThat(mov.getSancion()).isEqualByComparingTo("40.00"); // 1000*2/100*2

        // Consistencia: el motor y el refrendar producen el mismo desglose para el mismo insumo.
        // Si divergieran (como antes de Pasada 2 en plazos ≠ 7 dias), este assert fallaria.
        assertThat(mov.getSancion()).isEqualByComparingTo(expected.sancion());
        assertThat(mov.getInteres()).isEqualByComparingTo(
                expected.interes().add(expected.almacen()).add(expected.gastosAdmin()));
        assertThat(mov.getMonto()).isEqualByComparingTo(expected.total());
    }

    // =========================================================================
    // F. Regla del maximo de refrendos preservada
    // =========================================================================

    @Test
    void refrendo_alcanzoMaximoRefrendos_lanzaBadRequest() {
        Contrato c = contratoRef(0, 7);
        c.setNumRefrendos(5);
        PlazoParametro pp = paramVigente();
        pp.setNumMaxRefrendos(5);
        when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.ignis.prestamil.exception.BadRequestException.class,
                () -> service.refrendar(refrendoRequest(BigDecimal.ZERO), "cajero1")
        )).hasMessageContaining("maximo de refrendos");

        // No debe haberse guardado ningun movimiento
        org.mockito.Mockito.verify(movimientoRepository, org.mockito.Mockito.never())
                .save(any(MovimientoContrato.class));
    }
}
