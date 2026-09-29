package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoOperacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cotización de movimientos (F1) contra los casos de referencia de COCAE (sección 3 del plan de
 * Finiquitos y Refrendos) y los casos límite. Tolerancia ±$0.01 (RN-10: COCAE redondea en puntos
 * inconsistentes).
 *
 * <p>Todos los fixtures llevan {@code porcGastosAdmin = 1%}: si el motor lo sumara al cobro
 * periódico, ningún total cuadraría con COCAE (GAP-09).</p>
 */
@ExtendWith(MockitoExtension.class)
class CalculoContratoCotizacionTest {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    @Mock
    ParametrosSistemaCache parametrosSistemaCache;

    CalculoContratoService motor;

    @BeforeEach
    void setUp() {
        motor = new CalculoContratoService(parametrosSistemaCache);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Interés + almacenaje de alhajas en COCAE: 1.13% + 0.60% = 1.73% ("Int x Per.", GAP-09). */
    private static ParametrosCalculo params(String porcInteres) {
        return new ParametrosCalculo(
                new BigDecimal(porcInteres),
                new BigDecimal("0.60"),     // porcAlmacen
                new BigDecimal("1.00"),     // porcGastosAdmin: NO debe cobrarse por periodo
                new BigDecimal("2.00"),     // porcSancionSemanal
                2,                          // dias de gracia (D.G.S.C.)
                true,
                new BigDecimal("16.00"),
                BigDecimal.ZERO);           // porcDescuentoInteres (RN-27, F4)
    }

    /** Contrato semanal de 4 periodos (plazo "04 SEM") con saldo = préstamo. */
    private static Contrato contrato(String saldo, LocalDate fechaContrato, LocalDate vencimiento) {
        Contrato c = new Contrato();
        c.setId(1L);
        c.setFolio("CTR-TEST");
        c.setMontoPrestamo(new BigDecimal(saldo));
        c.setSaldoCapital(new BigDecimal(saldo));
        c.setFechaContrato(fechaContrato);
        c.setFechaVencimiento(vencimiento);
        c.setFechaComercializacion(vencimiento.plusDays(15));
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setNumRefrendos(0);
        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        c.setPlazo(plazo);
        return c;
    }

    private static LocalDate d(int anio, int mes, int dia) {
        return LocalDate.of(anio, mes, dia);
    }

    private static BigDecimal interesTotal(CotizacionMovimiento c) {
        return c.desglose().interes().add(c.desglose().almacen());
    }

    private static void assertMonto(BigDecimal actual, String esperado) {
        assertThat(actual).isCloseTo(new BigDecimal(esperado), within(TOLERANCIA));
    }

    // =========================================================================
    // C1-C5, C12, C13: casos COCAE con todos los números
    // =========================================================================

    /**
     * id, operacion, saldo, porcInteres, fechaContrato, vencimiento, fechaOperacion, abono,
     * tipo, periodos (total, normales, extemporaneos), interesPorPeriodo, interes, sancion, iva, total,
     * fechaContratoNueva, vencimientoNueva, diasGraciaUsados.
     *
     * <p>Contrato 448: COCAE no muestra sus tasas; 1.016% + 0.60% se infiere de sus totales
     * (2,050 × 1.616% × 6 = 198.77, 33.13 por periodo).</p>
     */
    static Stream<Arguments> casosCocae() {
        return Stream.of(
                Arguments.of("C1 finiquito normal (1493)", TipoOperacion.FINIQUITO,
                        "1195.00", "1.13", d(2026, 7, 16), d(2026, 8, 13), d(2026, 8, 11), null,
                        TipoMovimiento.FI, 4, 4, 0, "20.67", "82.69", "0.00", "13.23", "1290.92",
                        null, null, 0),
                Arguments.of("C2 refrendo normal (1493)", TipoOperacion.REFRENDO,
                        "1195.00", "1.13", d(2026, 7, 16), d(2026, 8, 13), d(2026, 8, 11), null,
                        TipoMovimiento.RF, 4, 4, 0, "20.67", "82.69", "0.00", "13.23", "95.92",
                        d(2026, 8, 13), d(2026, 9, 10), 0),
                Arguments.of("C3 finiquito extemporaneo (448)", TipoOperacion.FINIQUITO,
                        "2050.00", "1.016", d(2026, 6, 30), d(2026, 7, 28), d(2026, 8, 11), null,
                        TipoMovimiento.FX, 6, 4, 2, "33.13", "198.77", "82.00", "44.92", "2375.69",
                        null, null, 0),
                Arguments.of("C4 refrendo extemporaneo (448)", TipoOperacion.REFRENDO,
                        "2050.00", "1.016", d(2026, 6, 30), d(2026, 7, 28), d(2026, 8, 11), null,
                        TipoMovimiento.RX, 6, 4, 2, "33.13", "198.77", "82.00", "44.92", "325.69",
                        d(2026, 8, 11), d(2026, 9, 8), 0),
                // RN-06 sin excepciones: en gracia el nuevo periodo empieza el dia que vencia (07/06),
                // no el dia del pago como muestra el historial de COCAE de 2023.
                Arguments.of("C5 refrendo en gracia RPG (1493, 2023)", TipoOperacion.REFRENDO,
                        "1195.00", "1.13", d(2023, 5, 10), d(2023, 6, 7), d(2023, 6, 9), null,
                        TipoMovimiento.RPG, 4, 4, 0, "20.67", "82.69", "0.00", "13.23", "95.92",
                        d(2023, 6, 7), d(2023, 7, 5), 2),
                // GAP-04: 8 dias de atraso con gracia 2 son 2 semanas extemporaneas, no 1.
                // COCAE cobra 839.02 porque su interes queda en 522.10; aqui 522.11 (dentro de ±0.01).
                Arguments.of("C12 cotizacion extemporanea (2609)", TipoOperacion.REFRENDO,
                        "5030.00", "1.13", d(2026, 7, 6), d(2026, 8, 3), d(2026, 8, 11), null,
                        TipoMovimiento.RX, 6, 4, 2, "87.01", "522.11", "201.20", "115.72", "839.02",
                        d(2026, 8, 17), d(2026, 9, 14), 0),
                // GAP-08: abono permitido en gracia; cobra 4 periodos y no 5.
                Arguments.of("C13 abono a capital en gracia (3513), abono 0", TipoOperacion.ABONO_CAPITAL,
                        "1510.00", "1.13", d(2026, 7, 13), d(2026, 8, 10), d(2026, 8, 11), "0",
                        TipoMovimiento.RC, 4, 4, 0, "26.12", "104.49", "0.00", "16.71", "121.20",
                        d(2026, 8, 10), d(2026, 9, 7), 1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casosCocae")
    void casosCocae_cuadranConCocae(String id, TipoOperacion operacion,
                                    String saldo, String porcInteres,
                                    LocalDate fechaContrato, LocalDate vencimiento, LocalDate fechaOperacion,
                                    String abono,
                                    TipoMovimiento tipo, int periodos, int normales, int extemporaneos,
                                    String interesPorPeriodo, String interes, String sancion, String iva,
                                    String total,
                                    LocalDate fechaContratoNueva, LocalDate vencimientoNuevo,
                                    int diasGraciaUsados) {
        Contrato c = contrato(saldo, fechaContrato, vencimiento);

        CotizacionMovimiento cot = motor.cotizar(c, params(porcInteres), operacion, fechaOperacion,
                null, abono != null ? new BigDecimal(abono) : null);

        assertThat(cot.tipoMovimiento()).isEqualTo(tipo);
        assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(periodos);
        assertThat(cot.situacion().periodosNormales()).isEqualTo(normales);
        assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(extemporaneos);
        assertThat(cot.situacion().diasGraciaUsados()).isEqualTo(diasGraciaUsados);
        assertThat(cot.periodosAplicados()).isEqualTo(periodos);
        assertMonto(cot.interesPorPeriodo(), interesPorPeriodo);
        assertMonto(interesTotal(cot), interes);
        assertMonto(cot.desglose().sancion(), sancion);
        assertMonto(cot.desglose().iva(), iva);
        assertMonto(cot.total(), total);
        assertThat(cot.fechaContratoNueva()).isEqualTo(fechaContratoNueva);
        assertThat(cot.fechaVencimientoNueva()).isEqualTo(vencimientoNuevo);
        if (vencimientoNuevo != null) {
            assertThat(cot.fechaComercializacionNueva()).isEqualTo(vencimientoNuevo.plusDays(15));
        }
    }

    @Test
    void c1_finiquito_cierraElContratoYLiquidaElSaldo() {
        Contrato c = contrato("1195.00", d(2026, 7, 16), d(2026, 8, 13));

        CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.FINIQUITO,
                d(2026, 8, 11), null, null);

        assertThat(cot.capital()).isEqualByComparingTo("1195.00");
        assertThat(cot.saldoNuevo()).isEqualByComparingTo("0");
        assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.FINIQUITADO);
        assertThat(cot.fechaComercializacionNueva()).isNull();
    }

    @Test
    void c2_refrendo_dejaElContratoVigenteSinTocarElSaldo() {
        Contrato c = contrato("1195.00", d(2026, 7, 16), d(2026, 8, 13));

        CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO,
                d(2026, 8, 11), null, null);

        assertThat(cot.saldoNuevo()).isEqualByComparingTo("1195.00");
        assertThat(cot.capital()).isEqualByComparingTo("0");
        assertThat(cot.abonoCapital()).isEqualByComparingTo("0");
        assertThat(cot.descuento()).isEqualByComparingTo("0");
        assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
        assertThat(cot.advertencias()).isEmpty();
    }

    // =========================================================================
    // C6-C11: casos de la reunión del 21-sep (tasas aproximadas de la voz de Jorge)
    // =========================================================================

    /** Contrato del 21-sep: saldo 400, "≈6.04 por periodo" → 1.51% (0.91% + 0.60%). */
    @Nested
    class ContratoDel21Sep {

        private final LocalDate fechaContrato = d(2026, 9, 3);
        private final LocalDate vencimiento = d(2026, 10, 1);
        private final LocalDate hoy = d(2026, 9, 21); // 18 dias → 3 periodos transcurridos

        @Test
        void c6_parcialVigente_pagaUno_recorreUnaSemana() {
            Contrato c = contrato("400.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.REFRENDO_PARCIAL,
                    hoy, 1, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RP);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(3);
            assertThat(cot.periodosMaximos()).isEqualTo(2);
            assertThat(cot.periodosAplicados()).isEqualTo(1);
            assertThat(cot.periodosNormalesAplicados()).isEqualTo(1);
            assertThat(cot.periodosExtemporaneosAplicados()).isZero();
            assertMonto(interesTotal(cot), "6.04");
            assertMonto(cot.desglose().iva(), "0.96");
            assertMonto(cot.total(), "7.00");
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 10));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 8));
            assertThat(cot.advertencias()).isEmpty();
        }

        @Test
        void c6_parcialConMasPeriodosQueElMaximo_seAjustaConAdvertencia() {
            Contrato c = contrato("400.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.REFRENDO_PARCIAL,
                    hoy, 10, null);

            assertThat(cot.periodosAplicados()).isEqualTo(2);
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 17));
            assertThat(cot.advertencias()).singleElement().asString().contains("10").contains("2");
        }

        @Test
        void c7_finiquitoVigente_cobraLosTresPeriodosTranscurridos() {
            Contrato c = contrato("400.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.FINIQUITO,
                    hoy, null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.FI);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(3);
            assertMonto(interesTotal(cot), "18.12");
            assertMonto(cot.desglose().iva(), "2.89");
            assertMonto(cot.total(), "421.01");
            assertThat(cot.saldoNuevo()).isEqualByComparingTo("0");
        }

        @Test
        void c8_refrendoConAbono20_interesConSaldoAnterior_abonoSinIva() {
            Contrato c = contrato("400.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.ABONO_CAPITAL,
                    hoy, null, new BigDecimal("20.00"));

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RC);
            // El abono no reduce el interes del periodo en que se paga (RN-13)
            assertMonto(interesTotal(cot), "18.12");
            assertMonto(cot.desglose().iva(), "2.89");
            assertThat(cot.abonoCapital()).isEqualByComparingTo("20.00");
            assertMonto(cot.total(), "41.01");
            assertThat(cot.saldoNuevo()).isEqualByComparingTo("380.00");
            // RN-06: fecha de contrato anterior + 3 periodos pagados
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 24));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 22));
        }
    }

    @Test
    void c9_sancionSeCalculaSobreElSaldoNoSobreElPrestamoOriginal() {
        // Préstamo original 1,945, saldo 400 tras abonos. 5 dias de atraso → 5 periodos (4+1).
        Contrato c = contrato("400.00", d(2026, 8, 1), d(2026, 8, 29));
        c.setMontoPrestamo(new BigDecimal("1945.00"));

        CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.REFRENDO,
                d(2026, 9, 3), null, null);

        assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
        assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(5);
        assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(1);
        assertThat(cot.desglose().sancion()).isEqualByComparingTo("8.00"); // 400 × 2%, no 1,945 × 2%
        assertMonto(interesTotal(cot), "30.20");                          // 400 × 1.51% × 5 (RN-09)
    }

    @Test
    void c10_parcialVencido_pagaCinco_cubrePrimeroLosDosExtemporaneos() {
        // RN-07 "en vencido": fecha contrato 10/08, vencio 07/09, hoy 21/09 → 6 periodos (4+2).
        Contrato c = contrato("865.00", d(2026, 8, 10), d(2026, 9, 7));

        CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                d(2026, 9, 21), 5, null);

        assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPX);
        assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(6);
        assertThat(cot.periodosMaximos()).isEqualTo(5);
        assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(2);
        assertThat(cot.periodosNormalesAplicados()).isEqualTo(3);
        assertThat(cot.desglose().sancion()).isEqualByComparingTo("34.60"); // 17.30 × 2
        // Jorge dijo "≈130" de memoria; con 1.73% la regla da 126.92.
        assertMonto(cot.total(), "126.92");
        assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 14));
        assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 12));
        assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
    }

    @Test
    void c11_parcialEnVenta83Semanas_maximo82_cubre79ExtemporaneosY3Normales() {
        LocalDate fechaContrato = d(2025, 3, 1);
        Contrato c = contrato("400.00", fechaContrato, fechaContrato.plusDays(28));
        c.setEstatus(EstatusContrato.EN_VENTA);
        LocalDate hoy = fechaContrato.plusDays(580); // 83 semanas iniciadas

        CotizacionMovimiento cot = motor.cotizar(c, params("0.91"), TipoOperacion.REFRENDO_PARCIAL,
                hoy, 83, null);

        assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(83);
        assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(79);
        assertThat(cot.periodosAplicados()).isEqualTo(82);
        assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(79);
        assertThat(cot.periodosNormalesAplicados()).isEqualTo(3);
        assertThat(cot.desglose().sancion()).isEqualByComparingTo("632.00"); // 400 × 2% × 79
        assertThat(cot.advertencias()).hasSize(1);
        // El total (≈2,372 según Jorge) no se fija: faltan las tasas reales de ese contrato.
    }

    // =========================================================================
    // Casos límite (sección 3)
    // =========================================================================

    @Nested
    class CasosLimite {

        private final LocalDate fechaContrato = d(2026, 9, 1);
        private final LocalDate vencimiento = d(2026, 9, 29);

        @Test
        void parcialConUnPeriodoTranscurrido_rechaza() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 9, 5), 1, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void parcialSinPeriodos_rechaza() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 9, 20), null, null))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 9, 20), 0, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void abonoMenorA20_rechaza() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.ABONO_CAPITAL,
                    d(2026, 9, 20), null, new BigDecimal("19.99")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("20");
        }

        @Test
        void abonoIgualOMayorAlSaldo_rechazaConUseFiniquitar() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.ABONO_CAPITAL,
                    d(2026, 9, 20), null, new BigDecimal("1000.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Finiquitar");
            assertThatThrownBy(() -> motor.cotizar(c, params("1.13"), TipoOperacion.ABONO_CAPITAL,
                    d(2026, 9, 20), null, new BigDecimal("1500.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Finiquitar");
        }

        @Test
        void pagoEnElUltimoDiaDeGracia_esRpgSinSancion() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO,
                    vencimiento.plusDays(2), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPG);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(4);
            assertThat(cot.situacion().diasGraciaUsados()).isEqualTo(2);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("0");
            assertThat(cot.fechaContratoNueva()).isEqualTo(vencimiento);
        }

        @Test
        void pagoAlDiaSiguienteDeLaGracia_esExtemporaneoConUnaSemana() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO,
                    vencimiento.plusDays(3), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(5);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(1);
            assertThat(cot.situacion().diasGraciaUsados()).isZero();
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("20.00"); // 1000 × 2% × 1
            assertThat(cot.fechaContratoNueva()).isEqualTo(fechaContrato.plusDays(35));
        }

        @Test
        void parcialVencidoQueNoAlcanzaAPonerseAlCorriente_sigueVencido() {
            // 45 dias → 7 periodos (4+3). Paga 1: nuevo vencimiento = +35 dias, sigue vencido.
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    fechaContrato.plusDays(45), 1, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPX);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(1);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("20.00"); // solo el ext cubierto
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(fechaContrato.plusDays(35));
            assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VENCIDO);
        }

        @Test
        void plazoMensual_cuenta30DiasFijosCruzandoFebrero() {
            // RN-01: 30 dias fijos. 30/01/2027 + 30 = 01/03/2027 (plusMonths daria 28/02).
            Contrato c = contrato("1000.00", d(2027, 1, 30), d(2027, 3, 1));
            c.getPlazo().setDiasPorPeriodo(30);
            c.getPlazo().setNumeroPeriodos(1);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO,
                    d(2027, 2, 20), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RF);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(1);
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2027, 3, 1));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2027, 3, 31));
        }

        @Test
        void plazoMensualExtemporaneo_cobraUnPeriodoMasYSancionPorSemanas() {
            // 9 dias de atraso: el periodo mensual iniciado se cobra completo (RN-03) y la sancion
            // es semanal: ceil(9/7) = 2 semanas.
            Contrato c = contrato("1000.00", d(2027, 1, 30), d(2027, 3, 1));
            c.getPlazo().setDiasPorPeriodo(30);
            c.getPlazo().setNumeroPeriodos(1);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO,
                    d(2027, 3, 10), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(2);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(1);
            assertThat(cot.situacion().semanasSancion()).isEqualTo(2);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("40.00");
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2027, 3, 31));
        }

        @Test
        void sancionApagada_extemporaneoSinSancion() {
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);
            ParametrosCalculo p = params("1.13");
            ParametrosCalculo sinSancion = new ParametrosCalculo(p.porcInteres(), p.porcAlmacen(),
                    p.porcGastosAdmin(), p.porcSancionSemanal(), p.diasGraciaSancion(), false, p.porcIva(),
                    p.porcDescuentoInteres());

            CotizacionMovimiento cot = motor.cotizar(c, sinSancion, TipoOperacion.REFRENDO,
                    vencimiento.plusDays(10), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(2);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("0");
        }
    }
}
