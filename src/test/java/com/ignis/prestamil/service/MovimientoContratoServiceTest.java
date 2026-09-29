package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.FolioNota;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoOperacion;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.TipoTarjeta;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.BancoRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.FolioNotaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CotizacionRequest;
import com.ignis.prestamil.request.MovimientoRequest;
import com.ignis.prestamil.request.PagoRequest;
import com.ignis.prestamil.request.RefrendoRequest;
import com.ignis.prestamil.response.CotizacionMovimientoResponse;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registro de movimientos con cobro (F3): {@code registrar} recalcula con el motor único, valida la
 * ventana de Cobro (RN-24), es idempotente por requestId y exige turno activo. Los casos C2 y C5 usan las
 * fechas exactas de COCAE gracias al reloj inyectado.
 */
@ExtendWith(MockitoExtension.class)
class MovimientoContratoServiceTest {

    /** Fecha de operación de los casos COCAE C1 a C4 (contratos 1493 y 448). */
    private static final LocalDate HOY = LocalDate.of(2026, 8, 11);

    @Mock MovimientoContratoRepository movimientoRepository;
    @Mock ContratoRepository contratoRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock TurnoRepository turnoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock FolioNotaRepository folioNotaRepository;
    @Mock BancoRepository bancoRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;

    // Motor y validación de pago REALES: el punto es verificar que registrar los consume correctamente
    CalculoContratoService calculoContratoService;
    MovimientoContratoService service;
    Turno turno;
    FolioNota folio;

    @BeforeEach
    void setUp() {
        calculoContratoService = new CalculoContratoService(parametrosSistemaCache);
        service = servicioEn(HOY);

        turno = new Turno();
        turno.setId(1);
        lenient().when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turno));
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        lenient().when(usuarioRepository.findByNombreUsuario("cajero1")).thenReturn(Optional.of(usuario));
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        lenient().when(movimientoRepository.save(any(MovimientoContrato.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(movimientoRepository.findByRequestId(any())).thenReturn(Optional.empty());

        // La sucursal ya emitió notas: la siguiente es la 27323 (numeración de COCAE)
        folio = new FolioNota();
        folio.setSucursalId(1);
        folio.setUltimoFolio(27322);
        lenient().when(folioNotaRepository.findBySucursalId(1)).thenReturn(Optional.of(folio));

        Banco bbva = new Banco();
        bbva.setId(1);
        bbva.setNombre("BBVA");
        bbva.setActivo(true);
        lenient().when(bancoRepository.findById(1)).thenReturn(Optional.of(bbva));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private MovimientoContratoService servicioEn(LocalDate hoy) {
        ZoneId zona = ZoneId.systemDefault();
        Clock clock = Clock.fixed(hoy.atTime(12, 30).atZone(zona).toInstant(), zona);
        return new MovimientoContratoService(movimientoRepository, contratoRepository, plazoParametroRepository,
                turnoRepository, usuarioRepository, folioNotaRepository, calculoContratoService,
                new CobroService(bancoRepository), clock);
    }

    /** Alhajas en COCAE: 1.13% + 0.60% ("Int x Per." = 1.73%). Los gastos admin NO se cobran (GAP-09). */
    private static PlazoParametro paramAlhajas() {
        PlazoParametro pp = new PlazoParametro();
        pp.setPorcInteres(new BigDecimal("1.1300"));
        pp.setPorcAlmacen(new BigDecimal("0.6000"));
        pp.setPorcGastosAdmin(new BigDecimal("1.0000"));
        pp.setPorcSancionSemanal(new BigDecimal("2.0000"));
        pp.setDiasGraciaSinInteres(2);
        pp.setAplicarSancionPorPeriodo(true);
        pp.setNumMaxRefrendos(0);
        return pp;
    }

    /** Contrato 1493 de COCAE (04 SEM, saldo 1,195) con el periodo vigente indicado. */
    private Contrato contrato1493(LocalDate fechaContrato, LocalDate vencimiento, PlazoParametro param) {
        Contrato c = new Contrato();
        c.setId(42L);
        c.setFolio("1493");
        c.setMontoPrestamo(new BigDecimal("1195.00"));
        c.setSaldoCapital(new BigDecimal("1195.00"));
        c.setMontoAvaluo(new BigDecimal("1500.00"));
        c.setNumRefrendos(0);
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setSucursalId(1);
        c.setFechaContrato(fechaContrato);
        c.setFechaVencimiento(vencimiento);
        c.setFechaComercializacion(vencimiento.plusDays(15));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        c.setPlazo(plazo);

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        PartidaContrato partida = new PartidaContrato();
        partida.setTipoPrenda(alhaja);
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(partida);
        c.setPartidas(partidas);

        lenient().when(contratoRepository.findWithLockById(42L)).thenReturn(Optional.of(c));
        lenient().when(contratoRepository.findById(42L)).thenReturn(Optional.of(c));
        lenient().when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(param));
        return c;
    }

    /** C2: 16/07 → 13/08/2026, operación el 11/08/2026 (26 días → 4 periodos). */
    private Contrato contratoC2() {
        return contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), paramAlhajas());
    }

    private static MovimientoRequest request(TipoOperacion operacion, PagoRequest pago) {
        MovimientoRequest r = new MovimientoRequest();
        r.setContratoId(42L);
        r.setTipoOperacion(operacion);
        r.setPago(pago);
        r.setRequestId("req-1");
        return r;
    }

    private static PagoRequest efectivo(String monto) {
        PagoRequest pago = new PagoRequest();
        pago.setEfectivo(new BigDecimal(monto));
        return pago;
    }

    private static PagoRequest mixto(String efectivo, String tarjeta) {
        PagoRequest pago = new PagoRequest();
        pago.setEfectivo(new BigDecimal(efectivo));
        pago.setTarjeta(new BigDecimal(tarjeta));
        pago.setTipoTarjeta(TipoTarjeta.DEBITO);
        pago.setTarjetaUltimos4("1234");
        pago.setBancoEmisorId(1);
        pago.setAutorizacion("A1B2C3");
        return pago;
    }

    private MovimientoContrato capturarMovimiento() {
        ArgumentCaptor<MovimientoContrato> captor = ArgumentCaptor.forClass(MovimientoContrato.class);
        verify(movimientoRepository).save(captor.capture());
        return captor.getValue();
    }

    // =========================================================================
    // C2 y C5: casos de COCAE de punta a punta
    // =========================================================================

    @Nested
    class CasosCocae {

        @Test
        void c2_refrendoNormal_cobra95_92_yElNuevoPeriodoEmpiezaElDiaQueVencia() {
            Contrato c = contratoC2();

            MovimientoResponse resp = service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RF);
            // 1,195 × 1.73% × 4 = 82.69; IVA 16% truncado = 13.23; sin gastos admin
            assertThat(mov.getInteres()).isEqualByComparingTo("82.69");
            assertThat(mov.getSancion()).isEqualByComparingTo("0.00");
            assertThat(mov.getIva()).isEqualByComparingTo("13.23");
            assertThat(mov.getMonto()).isEqualByComparingTo("95.92");
            assertThat(mov.getInteresPorPeriodo()).isCloseTo(new BigDecimal("20.67"), within(new BigDecimal("0.01")));
            assertThat(mov.getPeriodosNormales()).isEqualTo(4);
            assertThat(mov.getSemanasVencidas()).isZero();
            assertThat(mov.getDiasGraciaUsados()).isZero();
            assertThat(mov.getAbonoCapital()).isEqualByComparingTo("0");

            // RN-20: fecha y hora del servidor, turno, usuario y estado antes/después
            assertThat(mov.getFecha()).isEqualTo(LocalDateTime.of(2026, 8, 11, 12, 30));
            assertThat(mov.getTurno()).isSameAs(turno);
            assertThat(mov.getUsuario().getNombreUsuario()).isEqualTo("cajero1");
            assertThat(mov.getFechaContratoAnterior()).isEqualTo(LocalDate.of(2026, 7, 16));
            assertThat(mov.getFechaVencAnterior()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(mov.getSaldoAnterior()).isEqualByComparingTo("1195.00");
            assertThat(mov.getNumRefrendosAnterior()).isZero();
            assertThat(mov.getFechaContratoNueva()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(mov.getFechaVencNueva()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(mov.getSaldoNuevo()).isEqualByComparingTo("1195.00");
            assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.VIGENTE);
            assertThat(mov.getRequestId()).isEqualTo("req-1");

            // RN-06: el nuevo periodo empieza el día que vencía; comercialización = vencimiento + 15
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(c.getFechaComercializacion()).isEqualTo(LocalDate.of(2026, 9, 25));
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("1195.00");
            assertThat(c.getNumRefrendos()).isEqualTo(1);
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
            verify(contratoRepository).save(c);

            // Folio de nota consecutivo de la sucursal (RN-25)
            assertThat(mov.getFolioNota()).isEqualTo(27323);
            assertThat(folio.getUltimoFolio()).isEqualTo(27323);
            assertThat(resp.getFolioNota()).isEqualTo(27323);
            assertThat(resp.getTipo()).isEqualTo(TipoMovimiento.RF);
            assertThat(resp.getMonto()).isEqualByComparingTo("95.92");
        }

        @Test
        void c5_refrendoEnGracia_esRpgSinSancion_yNoRegalaLosDiasDeGracia() {
            service = servicioEn(LocalDate.of(2023, 6, 9));
            Contrato c = contrato1493(LocalDate.of(2023, 5, 10), LocalDate.of(2023, 6, 7), paramAlhajas());

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RPG);
            assertThat(mov.getMonto()).isEqualByComparingTo("95.92");
            assertThat(mov.getSancion()).isEqualByComparingTo("0.00");
            assertThat(mov.getPeriodosNormales()).isEqualTo(4);
            assertThat(mov.getDiasGraciaUsados()).isEqualTo(2);
            // Regla única RN-06: vencía el 07/06 y pagó el 09/06 → el periodo empieza el 07/06
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2023, 6, 7));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 7, 5));
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        }

        @Test
        void c2_conSnapshot_usaLasTasasDelContratoNoLaConfigVigente() {
            PlazoParametro disparatado = paramAlhajas();
            disparatado.setPorcInteres(new BigDecimal("999"));
            disparatado.setPorcAlmacen(new BigDecimal("999"));
            Contrato c = contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), disparatado);
            c.setSnapPorcInteres(new BigDecimal("1.1300"));
            c.setSnapPorcAlmacen(new BigDecimal("0.6000"));
            c.setSnapPorcGastosAdmin(new BigDecimal("1.0000"));
            c.setSnapPorcSancionSemanal(new BigDecimal("2.0000"));
            c.setSnapDiasGraciaSancion(2);
            c.setSnapAplicarSancionPeriodo(true);
            c.setSnapIvaPorcentaje(new BigDecimal("16.00"));

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1");

            assertThat(capturarMovimiento().getMonto()).isEqualByComparingTo("95.92");
        }

        @Test
        void c1_finiquito_cierraElContratoYLiberaLasPartidas() {
            Contrato c = contratoC2();

            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("1290.92")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.FI);
            assertThat(mov.getMonto()).isEqualByComparingTo("1290.92");
            assertThat(mov.getSaldoNuevo()).isEqualByComparingTo("0");
            assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.FINIQUITADO);
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("0");
            assertThat(c.getPartidas()).allMatch(p -> p.getEstatus() == EstatusPartida.FIN);
            // El finiquito no es un refrendo y conserva las fechas del último periodo
            assertThat(c.getNumRefrendos()).isZero();
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 8, 13));
        }
    }

    // =========================================================================
    // F4: Finiquito y descuento sobre intereses (RN-15, RN-27)
    // =========================================================================

    @Nested
    class Finiquito {

        /** Contrato C7 del plan: saldo 400 con 3 periodos transcurridos → finiquito vigente. */
        private Contrato contratoC7(PlazoParametro param) {
            service = servicioEn(LocalDate.of(2026, 9, 21));
            param.setPorcInteres(new BigDecimal("0.9100"));
            Contrato c = contrato1493(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 1), param);
            c.setMontoPrestamo(new BigDecimal("400.00"));
            c.setSaldoCapital(new BigDecimal("400.00"));
            return c;
        }

        @Test
        void c7_finiquitoVigenteSinDescuento_cierraElContratoYLiberaLasPartidas() {
            Contrato c = contratoC7(paramAlhajas());

            // 400 × (0.91% + 0.60%) × 3 = 18.12; IVA 16% (DOWN) = 2.89; total = 400 + 18.12 + 2.89
            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("421.01")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.FI);
            assertThat(mov.getInteres()).isEqualByComparingTo("18.12");
            assertThat(mov.getIva()).isEqualByComparingTo("2.89");
            assertThat(mov.getMonto()).isEqualByComparingTo("421.01");
            assertThat(mov.getPorcDescuentoInteres()).isEqualByComparingTo("0.00");
            assertThat(mov.getImporteDescuento()).isEqualByComparingTo("0.00");
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("0");
            assertThat(c.getPartidas()).allMatch(p -> p.getEstatus() == EstatusPartida.FIN);
        }

        @Test
        void descuento10PorCiento_reduceInteresYSuIvaAntesDeCobrar() {
            PlazoParametro param = paramAlhajas();
            param.setPorcDescuentoInteres(new BigDecimal("10.0000"));
            contratoC7(param);

            // Interes bruto 18.12; descuento 10% = 1.81; base IVA = 18.12 − 1.81 = 16.31;
            // IVA 16% (DOWN) = 2.60; total = 400 + 16.31 + 2.60 = 418.91
            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("418.91")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getInteres()).isEqualByComparingTo("18.12");
            assertThat(mov.getImporteDescuento()).isEqualByComparingTo("1.81");
            assertThat(mov.getPorcDescuentoInteres()).isEqualByComparingTo("10.00");
            assertThat(mov.getIva()).isEqualByComparingTo("2.60");
            assertThat(mov.getMonto()).isEqualByComparingTo("418.91");
        }

        @Test
        void descuentoCero_esEquivalenteASinDescuento() {
            PlazoParametro param = paramAlhajas();
            param.setPorcDescuentoInteres(BigDecimal.ZERO);
            contratoC7(param);

            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("421.01")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getImporteDescuento()).isEqualByComparingTo("0.00");
            assertThat(mov.getMonto()).isEqualByComparingTo("421.01");
        }

        @Test
        void descuento10PorCiento_tambienAplicaEnRefrendo() {
            // RN-27 aplica a cualquier movimiento (refrendo, parcial, finiquito), no solo al finiquito.
            PlazoParametro param = paramAlhajas();
            param.setPorcDescuentoInteres(new BigDecimal("10.0000"));
            contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), param);

            // C2 sin descuento: interes 82.69, IVA 13.23, total 95.92. Con 10%:
            //   descuento = 8.27; base IVA = 74.42; IVA = 11.90 (DOWN); total = 86.32
            service.registrar(request(TipoOperacion.REFRENDO, efectivo("86.32")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getInteres()).isEqualByComparingTo("82.69");
            assertThat(mov.getImporteDescuento()).isEqualByComparingTo("8.27");
            assertThat(mov.getPorcDescuentoInteres()).isEqualByComparingTo("10.00");
            assertThat(mov.getIva()).isEqualByComparingTo("11.90");
            assertThat(mov.getMonto()).isEqualByComparingTo("86.32");
        }

        @Test
        void finiquitoEnVencido_seResuelveComoFx() {
            // Un contrato vencido resuelve TipoOperacion.FINIQUITO como FX (F6). El botón "Finiquitar"
            // del detalle está deshabilitado por accionesDisponibles; los tests de FX viven en F6.
            service = servicioEn(LocalDate.of(2026, 8, 20));
            contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), paramAlhajas());

            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("2000.00")), "cajero1");

            assertThat(capturarMovimiento().getTipo()).isEqualTo(TipoMovimiento.FX);
        }

        @Test
        void finiquitoSinTurnoActivo_400_yNoTocaElContrato() {
            Contrato c = contratoC7(paramAlhajas());
            when(turnoRepository.findByActivo(true)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.FINIQUITO, efectivo("421.01")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("turno activo");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("400.00");
            assertThat(c.getPartidas()).allMatch(p -> p.getEstatus() != EstatusPartida.FIN);
        }
    }

    // =========================================================================
    // F6: Refrendo y finiquito extemporáneos (RN-05, RN-11, RN-16, RN-17)
    // =========================================================================

    @Nested
    class Extemporaneos {

        /**
         * Contrato 448 (COCAE): saldo 2,050, tasas 1.016% + 0.60%, sanción 2% semanal, gracia 2 días.
         * Con fecha_contrato 30/06 y venc 28/07, operado el 11/08: 14 días de atraso → 2 semanas ext,
         * 6 periodos transcurridos (4 normales + 2 extemp).
         */
        private Contrato contrato448() {
            service = servicioEn(LocalDate.of(2026, 8, 11));
            PlazoParametro param = paramAlhajas();
            param.setPorcInteres(new BigDecimal("1.0160"));
            Contrato c = contrato1493(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 28), param);
            c.setMontoPrestamo(new BigDecimal("2050.00"));
            c.setSaldoCapital(new BigDecimal("2050.00"));
            return c;
        }

        @Test
        void c3_finiquitoExtemporaneo_cierraElContratoConSancionEIvaSobreInteresMasSancion() {
            // C3: interés 198.77, sanción 82.00, IVA 44.92 sobre (198.77+82); total = capital + 325.69
            Contrato c = contrato448();

            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("2375.69")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.FX);
            assertThat(mov.getPeriodosNormales()).isEqualTo(4);
            assertThat(mov.getSemanasVencidas()).isEqualTo(2);
            assertThat(mov.getInteres()).isEqualByComparingTo("198.77");
            assertThat(mov.getSancion()).isEqualByComparingTo("82.00");
            assertThat(mov.getIva()).isEqualByComparingTo("44.92");
            assertThat(mov.getMonto()).isEqualByComparingTo("2375.69");

            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("0");
            assertThat(c.getPartidas()).allMatch(p -> p.getEstatus() == EstatusPartida.FIN);
        }

        @Test
        void c4_refrendoExtemporaneo_dejaContratoVigenteConFechasPorRn06() {
            // C4: mismo desglose que C3 pero sin capital; total 325.69; fechas 30/06+6×7 = 11/08 → 08/09
            Contrato c = contrato448();

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("325.69")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RX);
            assertThat(mov.getPeriodosNormales()).isEqualTo(4);
            assertThat(mov.getSemanasVencidas()).isEqualTo(2);
            assertThat(mov.getInteres()).isEqualByComparingTo("198.77");
            assertThat(mov.getSancion()).isEqualByComparingTo("82.00");
            assertThat(mov.getIva()).isEqualByComparingTo("44.92");
            assertThat(mov.getMonto()).isEqualByComparingTo("325.69");

            assertThat(mov.getFechaContratoNueva()).isEqualTo(LocalDate.of(2026, 8, 11));
            assertThat(mov.getFechaVencNueva()).isEqualTo(LocalDate.of(2026, 9, 8));
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 8, 11));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 8));
            assertThat(c.getFechaComercializacion()).isEqualTo(LocalDate.of(2026, 9, 23));
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("2050.00");
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
            assertThat(c.getNumRefrendos()).isEqualTo(1);
        }

        @Test
        void c12_atraso8Dias_dosSemanasSancionables_registraRx() {
            // C12 (GAP-04): 8 días de atraso NO se descuenta la gracia una vez rebasada; son 2 semanas.
            // Saldo 5,030, tasas 1.13% + 0.60%. 6 periodos (4 normales + 2 ext). Interés 522.11, sanción
            // 201.20, IVA 115.72 (DOWN), total 839.03 (COCAE muestra 839.02 por su redondeo).
            service = servicioEn(LocalDate.of(2026, 8, 11));
            PlazoParametro param = paramAlhajas();
            Contrato c = contrato1493(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 3), param);
            c.setMontoPrestamo(new BigDecimal("5030.00"));
            c.setSaldoCapital(new BigDecimal("5030.00"));

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("839.03")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RX);
            assertThat(mov.getSemanasVencidas()).isEqualTo(2);
            assertThat(mov.getInteres()).isEqualByComparingTo("522.11");
            assertThat(mov.getSancion()).isEqualByComparingTo("201.20");
            assertThat(mov.getIva()).isEqualByComparingTo("115.72");
            assertThat(mov.getMonto()).isEqualByComparingTo("839.03");
            // Fechas por RN-06: 06/07 + 6×7 = 17/08 → 14/09
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 8, 17));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 14));
        }

        @Test
        void enVentaRecuperable_refrendoExtemporaneoDejaElContratoVigente() {
            // Estatus persistido EN_VENTA (pase diario ya lo marcó) con partidas OP: se puede recuperar
            // (RN-17). El refrendo lo devuelve a VIGENTE con las fechas por RN-06.
            service = servicioEn(LocalDate.of(2026, 8, 11));
            Contrato c = contrato448();
            c.setEstatus(EstatusContrato.EN_VENTA);

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("325.69")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RX);
            assertThat(mov.getEstatusAnterior()).isEqualTo(EstatusContrato.EN_VENTA);
            assertThat(mov.getEstatusNuevo()).isEqualTo(EstatusContrato.VIGENTE);
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        }

        @Test
        void refrendoExtemporaneoConPartidaApartada_400() {
            // RN-17: basta una partida apartada para bloquear todo el contrato.
            Contrato c = contrato448();
            c.getPartidas().get(0).setEstatus(EstatusPartida.APA);

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.REFRENDO, efectivo("325.69")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
        }

        @Test
        void finiquitoExtemporaneoConPartidaApartada_400() {
            Contrato c = contrato448();
            c.getPartidas().get(0).setEstatus(EstatusPartida.APA);

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.FINIQUITO, efectivo("2375.69")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
            assertThat(c.getEstatus()).isNotEqualTo(EstatusContrato.FINIQUITADO);
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("2050.00");
        }

        @Test
        void refrendoExtemporaneoConContratoVendido_400() {
            // Contrato con estatus VENDIDO (una prenda ya se vendió): fuera de operación.
            Contrato c = contrato448();
            c.setEstatus(EstatusContrato.VENDIDO);
            c.getPartidas().get(0).setEstatus(EstatusPartida.VEN);

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.REFRENDO, efectivo("325.69")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void finiquitoExtemporaneoConContratoVendido_400() {
            Contrato c = contrato448();
            c.setEstatus(EstatusContrato.VENDIDO);
            c.getPartidas().get(0).setEstatus(EstatusPartida.VEN);

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.FINIQUITO, efectivo("2375.69")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
            verify(movimientoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // F5: Abono a capital (RN-13)
    // =========================================================================

    @Nested
    class AbonoCapital {

        /** Contrato C8 del plan: 21-sep, saldo 400, 3 periodos transcurridos (tasa 0.91% + 0.60%). */
        private Contrato contratoC8(PlazoParametro param) {
            service = servicioEn(LocalDate.of(2026, 9, 21));
            param.setPorcInteres(new BigDecimal("0.9100"));
            Contrato c = contrato1493(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 1), param);
            c.setMontoPrestamo(new BigDecimal("400.00"));
            c.setSaldoCapital(new BigDecimal("400.00"));
            return c;
        }

        private MovimientoRequest requestConAbono(String abono, PagoRequest pago) {
            MovimientoRequest r = new MovimientoRequest();
            r.setContratoId(42L);
            r.setTipoOperacion(TipoOperacion.ABONO_CAPITAL);
            r.setAbonoCapital(new BigDecimal(abono));
            r.setPago(pago);
            r.setRequestId("req-abono");
            return r;
        }

        @Test
        void c8_abono20SobreSaldo400_registraRcSinIvaSobreElAbono_saldoNuevo380() {
            Contrato c = contratoC8(paramAlhajas());

            // 400 × (0.91% + 0.60%) × 3 = 18.12; IVA 16% (DOWN) = 2.89; +20 abono = 41.01
            service.registrar(requestConAbono("20.00", efectivo("41.01")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RC);
            assertThat(mov.getInteres()).isEqualByComparingTo("18.12");
            assertThat(mov.getSancion()).isEqualByComparingTo("0.00");
            // RN-13: IVA solo sobre intereses; el abono NO lleva IVA
            assertThat(mov.getIva()).isEqualByComparingTo("2.89");
            assertThat(mov.getAbonoCapital()).isEqualByComparingTo("20.00");
            assertThat(mov.getMonto()).isEqualByComparingTo("41.01");
            // Saldo nuevo = 400 − 20 = 380 (aplica al siguiente movimiento)
            assertThat(mov.getSaldoAnterior()).isEqualByComparingTo("400.00");
            assertThat(mov.getSaldoNuevo()).isEqualByComparingTo("380.00");
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("380.00");
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
            // RN-06: nueva fecha de contrato = fecha anterior + 3 periodos (21 días)
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 9, 24));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 22));
        }

        @Test
        void c13_abonoEnPeriodoDeGracia_permitidoYCobra4Periodos() {
            // Contrato 3513 (F5, GAP-08): venció el 10/08, hoy 11/08 → 1 día de gracia
            service = servicioEn(LocalDate.of(2026, 8, 11));
            PlazoParametro param = paramAlhajas();
            Contrato c = contrato1493(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 8, 10), param);
            c.setMontoPrestamo(new BigDecimal("1510.00"));
            c.setSaldoCapital(new BigDecimal("1510.00"));

            // Con abono 0 (modal abierto, aún sin capturar): total = importe por refrendo = 121.20
            service.registrar(requestConAbono("0", efectivo("121.20")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RC);
            assertThat(mov.getPeriodosNormales()).isEqualTo(4);
            // 1 día de gracia usado (no 5 periodos: cobra 4 como refrendo normal)
            assertThat(mov.getDiasGraciaUsados()).isEqualTo(1);
            assertThat(mov.getInteres()).isEqualByComparingTo("104.49");
            assertThat(mov.getIva()).isEqualByComparingTo("16.71");
            assertThat(mov.getAbonoCapital()).isEqualByComparingTo("0");
            assertThat(mov.getMonto()).isEqualByComparingTo("121.20");
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("1510.00");
            // Fechas por RN-06: nueva fecha contrato = la que vencía (10/08), no la del pago
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 8, 10));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 7));
        }

        @Test
        void abonoMenorAMinimo_400_yNoTocaElContrato() {
            Contrato c = contratoC8(paramAlhajas());

            assertThatThrownBy(() -> service.registrar(
                    requestConAbono("15.00", efectivo("36.01")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("20");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("400.00");
        }

        @Test
        void abonoIgualAlSaldo_400ConMensajeUseFiniquitar() {
            Contrato c = contratoC8(paramAlhajas());

            assertThatThrownBy(() -> service.registrar(
                    requestConAbono("400.00", efectivo("421.01")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Finiquitar");
            verify(movimientoRepository, never()).save(any());
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("400.00");
        }

        @Test
        void abonoEnContratoVencido_400_porMatrizRn16() {
            // Contrato C2 pero operado 10 días después del vencimiento → VENCIDO, sin ABONO_CAPITAL
            service = servicioEn(LocalDate.of(2026, 8, 23));
            Contrato c = contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), paramAlhajas());

            assertThatThrownBy(() -> service.registrar(
                    requestConAbono("100.00", efectivo("500.00")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("1195.00");
        }

        @Test
        void abonoLuegoRefrendo_elSegundoUsaElSaldoNuevo() {
            // 1) Abono 100 en el contrato C8 (saldo 400 → 300, fechas 24/09 → 22/10)
            Contrato c = contratoC8(paramAlhajas());
            // 400 × 1.51% × 3 = 18.12, IVA 2.89 → refrendo 21.01 + 100 abono = 121.01
            service.registrar(requestConAbono("100.00", efectivo("121.01")), "cajero1");
            MovimientoContrato primero = capturarMovimiento();
            assertThat(primero.getTipo()).isEqualTo(TipoMovimiento.RC);
            assertThat(primero.getSaldoNuevo()).isEqualByComparingTo("300.00");
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("300.00");
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 9, 24));
            assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 22));

            // 2) Refrendo el día del nuevo vencimiento: 4 periodos sobre el SALDO NUEVO 300
            //    300 × (0.91% + 0.60%) × 4 = 18.12; IVA 16% (DOWN) = 2.89; total = 21.01
            service = servicioEn(LocalDate.of(2026, 10, 22));
            MovimientoRequest req = request(TipoOperacion.REFRENDO, efectivo("21.01"));
            req.setRequestId("req-segundo");
            service.registrar(req, "cajero1");

            ArgumentCaptor<MovimientoContrato> captor = ArgumentCaptor.forClass(MovimientoContrato.class);
            verify(movimientoRepository, times(2)).save(captor.capture());
            MovimientoContrato segundo = captor.getAllValues().get(1);
            assertThat(segundo.getTipo()).isEqualTo(TipoMovimiento.RF);
            assertThat(segundo.getSaldoAnterior()).isEqualByComparingTo("300.00");
            // El interés del segundo se calcula sobre 300, no sobre 400 (RN-13: el nuevo saldo aplica al siguiente movimiento)
            assertThat(segundo.getInteres()).isEqualByComparingTo("18.12");
            assertThat(segundo.getIva()).isEqualByComparingTo("2.89");
            assertThat(segundo.getMonto()).isEqualByComparingTo("21.01");
            // El refrendo no reduce el saldo
            assertThat(segundo.getSaldoNuevo()).isEqualByComparingTo("300.00");
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("300.00");
            assertThat(c.getNumRefrendos()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Ventana de Cobro (RN-24)
    // =========================================================================

    @Nested
    class VentanaDeCobro {

        @Test
        void pagoMixto_efectivoMasTarjetaIgualAlTotal_guardaSoloLosUltimos4DeLaTarjeta() {
            contratoC2();

            service.registrar(request(TipoOperacion.REFRENDO, mixto("50.00", "45.92")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getImporteEfectivo()).isEqualByComparingTo("50.00");
            assertThat(mov.getImporteTarjeta()).isEqualByComparingTo("45.92");
            assertThat(mov.getCambioEntregado()).isEqualByComparingTo("0.00");
            assertThat(mov.getTipoTarjeta()).isEqualTo(TipoTarjeta.DEBITO);
            assertThat(mov.getTarjetaUltimos4()).isEqualTo("1234");
            assertThat(mov.getBancoEmisor().getNombre()).isEqualTo("BBVA");
            assertThat(mov.getAutorizacionBanco()).isEqualTo("A1B2C3");
        }

        @Test
        void efectivoMayorAlTotal_calculaElCambio() {
            contratoC2();

            MovimientoResponse resp = service.registrar(request(TipoOperacion.REFRENDO, efectivo("100.00")), "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getImporteEfectivo()).isEqualByComparingTo("100.00");
            assertThat(mov.getCambioEntregado()).isEqualByComparingTo("4.08");
            assertThat(mov.getImporteTarjeta()).isEqualByComparingTo("0");
            assertThat(mov.getTarjetaUltimos4()).isNull();
            assertThat(resp.getCambioEntregado()).isEqualByComparingTo("4.08");
        }

        @Test
        void pagoMixtoConCambio_elCambioSaleDelEfectivo() {
            contratoC2();

            service.registrar(request(TipoOperacion.REFRENDO, mixto("60.00", "45.92")), "cajero1");

            assertThat(capturarMovimiento().getCambioEntregado()).isEqualByComparingTo("10.00");
        }

        @Test
        void tarjetaMayorAlTotal_400() {
            contratoC2();

            assertThatThrownBy(() -> service.registrar(
                    request(TipoOperacion.REFRENDO, mixto("0", "100.00")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("tarjeta");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
        }

        @Test
        void tarjetaSinAutorizacion_400() {
            contratoC2();
            PagoRequest pago = mixto("0", "95.92");
            pago.setAutorizacion(" ");

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, pago), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("autorización");
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void tarjetaConBancoInactivo_400() {
            contratoC2();
            Banco inactivo = new Banco();
            inactivo.setId(2);
            inactivo.setActivo(false);
            when(bancoRepository.findById(2)).thenReturn(Optional.of(inactivo));
            PagoRequest pago = mixto("0", "95.92");
            pago.setBancoEmisorId(2);

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, pago), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("banco");
        }

        @Test
        void pagoQueNoCubreElTotal_400() {
            contratoC2();

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.91")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no cubre");
            verify(movimientoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // Idempotencia, turno, cotización previa y máximo de refrendos
    // =========================================================================

    @Nested
    class Validaciones {

        @Test
        void requestIdRepetido_devuelveElMismoMovimientoSinCobrarDosVeces() {
            Contrato c = contratoC2();
            MovimientoRequest req = request(TipoOperacion.REFRENDO, efectivo("95.92"));

            MovimientoResponse primero = service.registrar(req, "cajero1");
            MovimientoContrato registrado = capturarMovimiento();
            // Doble clic: la segunda petición llega con el mismo requestId
            when(movimientoRepository.findByRequestId("req-1")).thenReturn(Optional.of(registrado));
            MovimientoResponse segundo = service.registrar(req, "cajero1");

            verify(movimientoRepository, times(1)).save(any());
            assertThat(segundo.getFolioNota()).isEqualTo(primero.getFolioNota());
            assertThat(segundo.getMonto()).isEqualByComparingTo(primero.getMonto());
            // El contrato se refrendó una sola vez
            assertThat(c.getNumRefrendos()).isEqualTo(1);
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(folio.getUltimoFolio()).isEqualTo(27323);
        }

        @Test
        void requestIdUsadoEnOtroContrato_400() {
            contratoC2();
            Contrato otro = new Contrato();
            otro.setId(99L);
            MovimientoContrato ajeno = new MovimientoContrato();
            ajeno.setContrato(otro);
            when(movimientoRepository.findByRequestId("req-1")).thenReturn(Optional.of(ajeno));

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1"))
                    .isInstanceOf(BadRequestException.class);
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void sinTurnoActivo_400_yNoTocaElContrato() {
            Contrato c = contratoC2();
            when(turnoRepository.findByActivo(true)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("turno activo");
            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
            assertThat(c.getFechaContrato()).isEqualTo(LocalDate.of(2026, 7, 16));
            assertThat(folio.getUltimoFolio()).isEqualTo(27322);
        }

        @Test
        void contratoInexistente_404() {
            when(contratoRepository.findWithLockById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void importeDistintoAlCotizado_400_paraVolverACotizar() {
            contratoC2();
            MovimientoRequest req = request(TipoOperacion.REFRENDO, efectivo("100.00"));
            req.setTotalCotizado(new BigDecimal("90.00"));

            assertThatThrownBy(() -> service.registrar(req, "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Vuelva a cotizar");
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void importeIgualAlCotizado_registra() {
            contratoC2();
            MovimientoRequest req = request(TipoOperacion.REFRENDO, efectivo("95.92"));
            req.setTotalCotizado(new BigDecimal("95.92"));

            service.registrar(req, "cajero1");

            assertThat(capturarMovimiento().getMonto()).isEqualByComparingTo("95.92");
        }

        @Test
        void refrendoFueraDeLaMatriz_400() {
            Contrato c = contratoC2();
            c.getPartidas().get(0).setEstatus(EstatusPartida.APA);

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no está disponible");
        }

        @Test
        void maximoDeRefrendosAlcanzado_400_soloSePuedeFiniquitar() {
            PlazoParametro electronicos = paramAlhajas();
            electronicos.setNumMaxRefrendos(5);
            Contrato c = contrato1493(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 13), electronicos);
            c.setNumRefrendos(5);

            assertThatThrownBy(() -> service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("máximo de refrendos");
            verify(movimientoRepository, never()).save(any());

            // El finiquito sigue disponible
            service.registrar(request(TipoOperacion.FINIQUITO, efectivo("1290.92")), "cajero1");
            assertThat(c.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
        }

        @Test
        void maximoDeRefrendosCero_esSinLimite() {
            Contrato c = contratoC2();
            c.setNumRefrendos(80);

            service.registrar(request(TipoOperacion.REFRENDO, efectivo("95.92")), "cajero1");

            assertThat(c.getNumRefrendos()).isEqualTo(81);
        }
    }

    // =========================================================================
    // Endpoint deprecado /refrendo
    // =========================================================================

    @Nested
    @SuppressWarnings("deprecation")
    class RefrendoDeprecado {

        @Test
        void conAbono_registraRcPagadoEnEfectivoExacto() {
            Contrato c = contratoC2();
            RefrendoRequest r = new RefrendoRequest();
            r.setIdContrato(42L);
            r.setAbonoCapital(new BigDecimal("100.00"));

            service.refrendar(r, "cajero1");

            MovimientoContrato mov = capturarMovimiento();
            assertThat(mov.getTipo()).isEqualTo(TipoMovimiento.RC);
            // El abono no lleva IVA ni reduce el interés del periodo en que se paga (RN-13)
            assertThat(mov.getMonto()).isEqualByComparingTo("195.92");
            assertThat(mov.getImporteEfectivo()).isEqualByComparingTo("195.92");
            assertThat(mov.getCambioEntregado()).isEqualByComparingTo("0");
            assertThat(mov.getRequestId()).isNotBlank();
            assertThat(c.getSaldoCapital()).isEqualByComparingTo("1095.00");
        }

        @Test
        void sinAbono_registraRefrendo() {
            contratoC2();
            RefrendoRequest r = new RefrendoRequest();
            r.setIdContrato(42L);

            MovimientoResponse resp = service.refrendar(r, "cajero1");

            assertThat(resp.getTipo()).isEqualTo(TipoMovimiento.RF);
            assertThat(resp.getMonto()).isEqualByComparingTo("95.92");
        }
    }

    // =========================================================================
    // Cotización (F1): solo lectura, valida la acción contra la matriz RN-16
    // =========================================================================

    @Nested
    class Cotizacion {

        /** Contrato semanal de 4 periodos, préstamo = saldo = 1000, tasas 3% + 2% (+1% gastos admin). */
        private Contrato contratoCotizable(int diasAtraso) {
            PlazoParametro pp = paramAlhajas();
            pp.setPorcInteres(new BigDecimal("3.0000"));
            pp.setPorcAlmacen(new BigDecimal("2.0000"));
            LocalDate vencimiento = HOY.minusDays(diasAtraso);
            Contrato c = contrato1493(vencimiento.minusDays(28), vencimiento, pp);
            c.setFolio("CTR-000042");
            c.setMontoPrestamo(new BigDecimal("1000.00"));
            c.setSaldoCapital(new BigDecimal("1000.00"));
            return c;
        }

        private CotizacionRequest request(TipoOperacion operacion) {
            CotizacionRequest r = new CotizacionRequest();
            r.setContratoId(42L);
            r.setTipoOperacion(operacion);
            return r;
        }

        @Test
        void refrendoVigente_devuelveLaCotizacionYNoPersisteNada() {
            // Vence en 5 dias: 23 dias transcurridos → 4 periodos
            contratoCotizable(-5);

            CotizacionMovimientoResponse resp = service.cotizar(request(TipoOperacion.REFRENDO));

            assertThat(resp.getContratoId()).isEqualTo(42L);
            assertThat(resp.getFolio()).isEqualTo("CTR-000042");
            assertThat(resp.getTipoMovimiento()).isEqualTo(TipoMovimiento.RF);
            assertThat(resp.getEstatusActual()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(resp.getAccionesDisponibles()).contains(AccionContrato.REFRENDO, AccionContrato.FINIQUITO);
            assertThat(resp.getPeriodosTranscurridos()).isEqualTo(4);
            // 1000 × (3% + 2%) × 4 = 200; IVA 32; sin gastos admin (GAP-09)
            assertThat(resp.getInteresTotal()).isEqualByComparingTo("200.00");
            assertThat(resp.getSubtotal()).isEqualByComparingTo("200.00");
            assertThat(resp.getIva()).isEqualByComparingTo("32.00");
            assertThat(resp.getTotal()).isEqualByComparingTo("232.00");
            assertThat(resp.getTotalConLetra()).isEqualTo("DOSCIENTOS TREINTA Y DOS PESOS 00/100 M.N.");

            verify(movimientoRepository, never()).save(any());
            verify(contratoRepository, never()).save(any());
        }

        @Test
        void finiquitoEnContratoVencido_cotizaComoFxConSancion() {
            // F6: TipoOperacion.FINIQUITO en un contrato vencido resuelve a FX. Vence hace 10 días
            // (gracia 2, sancionables 10 → ceil(10/7) = 2 semanas): 4 periodos normales + 2 extemp.
            contratoCotizable(10);

            CotizacionMovimientoResponse resp = service.cotizar(request(TipoOperacion.FINIQUITO));

            assertThat(resp.getTipoMovimiento()).isEqualTo(TipoMovimiento.FX);
            assertThat(resp.getEstatusActual()).isEqualTo(EstatusOperativo.VENCIDO);
            assertThat(resp.getAccionesDisponibles()).contains(AccionContrato.FINIQUITO_EXTEMPORANEO);
            assertThat(resp.getPeriodosExtemporaneos()).isEqualTo(2);
            assertThat(resp.getSancion()).isPositive();
            verify(movimientoRepository, never()).save(any());
        }

        @Test
        void abonoEnContratoVencido_rechaza() {
            contratoCotizable(10);
            CotizacionRequest r = request(TipoOperacion.ABONO_CAPITAL);
            r.setAbonoCapital(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.cotizar(r)).isInstanceOf(BadRequestException.class);
        }

        @Test
        void refrendoConUnaPartidaApartada_rechaza() {
            Contrato c = contratoCotizable(10);
            c.getPartidas().get(0).setEstatus(EstatusPartida.APA);

            assertThatThrownBy(() -> service.cotizar(request(TipoOperacion.REFRENDO)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void conElMaximoDeRefrendosAlcanzado_soloOfreceFiniquitar() {
            Contrato c = contratoCotizable(-5);
            c.setNumRefrendos(3);
            PlazoParametro conMaximo = paramAlhajas();
            conMaximo.setNumMaxRefrendos(3);
            when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                    .thenReturn(Optional.of(conMaximo));

            CotizacionMovimientoResponse resp = service.cotizar(request(TipoOperacion.FINIQUITO));

            assertThat(resp.getAccionesDisponibles())
                    .contains(AccionContrato.FINIQUITO)
                    .doesNotContain(AccionContrato.REFRENDO, AccionContrato.ABONO_CAPITAL,
                            AccionContrato.REFRENDO_PARCIAL);
            assertThatThrownBy(() -> service.cotizar(request(TipoOperacion.REFRENDO)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("máximo de refrendos");
        }

        @Test
        void contratoInexistente_404() {
            when(contratoRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cotizar(request(TipoOperacion.REFRENDO)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // F8: Consulta de partidas y movimientos (getMovimientos)
    // =========================================================================

    /** El historial es la vista de auditoría del contrato: cronológico, incluyendo cancelados. */
    @Nested
    class Historial {

        private MovimientoContrato mov(Long id, TipoMovimiento tipo, LocalDateTime cuando, boolean cancelado) {
            Usuario u = new Usuario();
            u.setNombreUsuario("cajero1");
            MovimientoContrato m = new MovimientoContrato();
            m.setId(id);
            m.setTipo(tipo);
            m.setFecha(cuando);
            m.setMonto(BigDecimal.ZERO);
            m.setInteres(BigDecimal.ZERO);
            m.setSancion(BigDecimal.ZERO);
            m.setAbonoCapital(BigDecimal.ZERO);
            m.setIva(BigDecimal.ZERO);
            m.setSemanasVencidas(0);
            m.setUsuario(u);
            m.setCancelado(cancelado);
            return m;
        }

        @Test
        void devuelveMovimientosEnOrdenCronologicoIncluyendoCancelados() {
            Contrato c = contratoC2();
            MovimientoContrato emp = mov(1L, TipoMovimiento.EMP, LocalDateTime.of(2026, 7, 16, 10, 0), false);
            MovimientoContrato rfCancelado = mov(2L, TipoMovimiento.RF, LocalDateTime.of(2026, 8, 11, 12, 30), true);
            rfCancelado.setMotivoCancelacion("Se capturó el plazo equivocado");
            MovimientoContrato rf = mov(3L, TipoMovimiento.RF, LocalDateTime.of(2026, 8, 11, 12, 45), false);
            when(movimientoRepository.findByContratoIdOrderByFechaAsc(42L))
                    .thenReturn(List.of(emp, rfCancelado, rf));

            List<MovimientoResponse> resp = service.getMovimientos(42L);

            assertThat(resp).extracting(MovimientoResponse::getId).containsExactly(1L, 2L, 3L);
            assertThat(resp).extracting(MovimientoResponse::getTipo)
                    .containsExactly(TipoMovimiento.EMP, TipoMovimiento.RF, TipoMovimiento.RF);
            assertThat(resp.get(1).getCancelado()).isTrue();
            assertThat(resp.get(1).getMotivoCancelacion()).isEqualTo("Se capturó el plazo equivocado");
            assertThat(resp.get(2).getCancelado()).isFalse();
            // El folio del contrato viaja en cada fila (para armar el título del modal sin otra llamada)
            assertThat(resp).allMatch(r -> "1493".equals(r.getFolioContrato()));
        }

        @Test
        void contratoSinMovimientosDevuelveListaVacia() {
            contratoC2();
            when(movimientoRepository.findByContratoIdOrderByFechaAsc(42L)).thenReturn(List.of());

            assertThat(service.getMovimientos(42L)).isEmpty();
        }

        @Test
        void contratoInexistente_404() {
            when(contratoRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMovimientos(42L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
