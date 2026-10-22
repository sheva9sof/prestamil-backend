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
        void parcialVencidoConNMenorAlMinimo_seAjustaAExtemporaneosMasUno_saleDeVenta() {
            // D-03 (RN-31): 45 dias → 7 periodos (4+3). n=1 queda por debajo del mínimo
            // extemporáneos+1 = 4; se ajusta a 4 con aviso y el contrato regresa a VIGENTE.
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    fechaContrato.plusDays(45), 1, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPX);
            assertThat(cot.periodosMinimos()).isEqualTo(4);
            assertThat(cot.periodosMaximos()).isEqualTo(6);
            assertThat(cot.periodosAplicados()).isEqualTo(4);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(3);
            assertThat(cot.periodosNormalesAplicados()).isEqualTo(1);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("60.00"); // 1000 × 2% × 3 ext
            // FC' = FC + 4·7 = 29-sep; FV' = 29-sep + 28 = 27-oct
            assertThat(cot.fechaContratoNueva()).isEqualTo(fechaContrato.plusDays(28));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(fechaContrato.plusDays(56));
            assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(cot.advertencias()).singleElement().asString()
                    .contains("1").contains("4");
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

        /**
         * C14 (plan 07-oct §2.7): parcial de 1 periodo en un contrato vigente. FC 10-sep, FV 08-oct,
         * T 07-oct → 27 días transcurridos → 4 periodos iniciados (sin atraso). Parcial n=1 debe
         * cubrir 1 periodo y mover la fecha de contrato una semana (FC' 17-sep, FV' 15-oct).
         */
        @Test
        void c14_parcialDeUnPeriodo_contratoVigente_mueveFcUnaSemana() {
            Contrato c = contrato("7760.30", d(2026, 9, 10), d(2026, 10, 8));

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 10, 7), 1, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RP);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(4);
            assertThat(cot.situacion().periodosExtemporaneos()).isZero();
            assertThat(cot.periodosMaximos()).isEqualTo(3);
            assertThat(cot.periodosAplicados()).isEqualTo(1);
            assertThat(cot.periodosNormalesAplicados()).isEqualTo(1);
            assertThat(cot.periodosExtemporaneosAplicados()).isZero();
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 17));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 15));
            assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(cot.advertencias()).isEmpty();
        }

        /**
         * C16 (plan 07-oct §2.7): parcial de 1 periodo con el contrato en periodo de gracia.
         * FC 08-sep, FV 06-oct, T 07-oct → 29 días, 1 día de atraso ≤ 2 gracia. Debe cobrar 1 periodo,
         * sin sanción, mover FC a 15-sep y FV a 13-oct, y dejar el contrato en VIGENTE.
         */
        @Test
        void c16_parcialDeUnPeriodo_contratoEnGracia_sinSancion_estatusVigente() {
            Contrato c = contrato("1500.00", d(2026, 9, 8), d(2026, 10, 6));

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 10, 7), 1, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RP);
            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(4);
            assertThat(cot.situacion().periodosExtemporaneos()).isZero();
            assertThat(cot.situacion().diasGraciaUsados()).isEqualTo(1);
            assertThat(cot.periodosMaximos()).isEqualTo(3);
            assertThat(cot.periodosAplicados()).isEqualTo(1);
            assertThat(cot.periodosExtemporaneosAplicados()).isZero();
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("0");
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 15));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 13));
            assertThat(cot.fechaComercializacionNueva()).isEqualTo(d(2026, 10, 28));
            assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(cot.advertencias()).isEmpty();
        }

        /**
         * C17 (plan 07-oct §2.7): contrato en venta, 4 normales + 4 extemporáneos. En el parcial el
         * mínimo es extemporáneos + 1 = 5 y el máximo transcurridos − 1 = 7 (D-03, RN-31). Para que el
         * contrato cuadre con los números de Jorge (interés + almacenaje por periodo = 22.05) se usa
         * porcInteres = 2.90 (sumado al 0.60 del fixture).
         */
        @Test
        void c17_parcialEnVenta_minimoEsExtemporaneosMasUno_maximoEsTranscurridosMenosUno() {
            Contrato c = contrato("630.00", d(2026, 8, 16), d(2026, 9, 13));

            CotizacionMovimiento cot = motor.cotizar(c, params("2.90"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 10, 7), 7, null);

            assertThat(cot.situacion().periodosTranscurridos()).isEqualTo(8);
            assertThat(cot.situacion().periodosNormales()).isEqualTo(4);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(4);
            assertThat(cot.periodosMinimos()).isEqualTo(5);
            assertThat(cot.periodosMaximos()).isEqualTo(7);
            assertThat(cot.periodosAplicados()).isEqualTo(7);
            assertThat(cot.advertencias()).isEmpty();
        }

        /**
         * C18 (plan 07-oct §2.7): mismo contrato que C17 con n = 4. Al estar por debajo del mínimo
         * (5), el motor lo ajusta a 5 con aviso; FC' = 20-sep, FV' = 18-oct y el estatus pasa a
         * VIGENTE.
         */
        @Test
        void c18_parcialEnVenta_nMenorAlMinimo_seAjustaA5_dejaContratoVigente() {
            Contrato c = contrato("630.00", d(2026, 8, 16), d(2026, 9, 13));

            CotizacionMovimiento cot = motor.cotizar(c, params("2.90"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 10, 7), 4, null);

            assertThat(cot.periodosMinimos()).isEqualTo(5);
            assertThat(cot.periodosAplicados()).isEqualTo(5);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(4);
            assertThat(cot.periodosNormalesAplicados()).isEqualTo(1);
            // Sanción cubre los 4 extemporáneos: 630 × 2% × 4 = 50.40 (RN-31)
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("50.40");
            assertThat(cot.fechaContratoNueva()).isEqualTo(d(2026, 9, 20));
            assertThat(cot.fechaVencimientoNueva()).isEqualTo(d(2026, 10, 18));
            assertThat(cot.estatusNuevo()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(cot.advertencias()).singleElement().asString()
                    .contains("4").contains("5");
        }

        /**
         * D-03: en VIGENTE/EN_GRACIA el mínimo del parcial sigue siendo 1 (basta recorrer una semana).
         * Mismo contrato que C16: 1 día de atraso ≤ gracia 2 → EN_GRACIA.
         */
        @Test
        void parcialEnGracia_minimoEsUno() {
            Contrato c = contrato("1500.00", d(2026, 9, 8), d(2026, 10, 6));

            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"), TipoOperacion.REFRENDO_PARCIAL,
                    d(2026, 10, 7), 1, null);

            assertThat(cot.situacion().extemporaneo()).isFalse();
            assertThat(cot.situacion().diasGraciaUsados()).isEqualTo(1);
            assertThat(cot.periodosMinimos()).isEqualTo(1);
            assertThat(cot.periodosMaximos()).isEqualTo(3);
            assertThat(cot.periodosAplicados()).isEqualTo(1);
            assertThat(cot.advertencias()).isEmpty();
        }

        @Test
        void sancionApagadaConPorcentajeMayorACero_siguieCobrandoSancion() {
            // D-14 (2026-10-08): el gate aplicarSancion fue removido del motor. Un ParametrosCalculo
            // con porcSancionSemanal=2 y aplicarSancion=false YA NO cobra 0; cobra el 2% igual
            // que con el gate en true. "Sin sanción" ahora se expresa configurando el porcentaje
            // en 0, no apagando el toggle. Si el negocio quiere apagarla en un plazo, se guarda
            // porcSancionSemanal=0 explícitamente desde Plazos y Periodos.
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);
            ParametrosCalculo p = params("1.13");
            ParametrosCalculo gateOff = new ParametrosCalculo(p.porcInteres(), p.porcAlmacen(),
                    p.porcGastosAdmin(), p.porcSancionSemanal(), p.diasGraciaSancion(), false, p.porcIva(),
                    p.porcDescuentoInteres());

            CotizacionMovimiento cot = motor.cotizar(c, gateOff, TipoOperacion.REFRENDO,
                    vencimiento.plusDays(10), null, null);

            // 1000 × 2% × 2 semanas = 40.00. El gate false no la apaga.
            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(2);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("40.00");
        }

        @Test
        void porcSancionCero_desactivaSancion() {
            // D-14: la unica forma valida de "apagar sancion" es configurar porcSancionSemanal=0.
            Contrato c = contrato("1000.00", fechaContrato, vencimiento);
            ParametrosCalculo p = params("1.13");
            ParametrosCalculo sinSancion = new ParametrosCalculo(p.porcInteres(), p.porcAlmacen(),
                    p.porcGastosAdmin(), BigDecimal.ZERO, p.diasGraciaSancion(), p.aplicarSancion(),
                    p.porcIva(), p.porcDescuentoInteres());

            CotizacionMovimiento cot = motor.cotizar(c, sinSancion, TipoOperacion.REFRENDO,
                    vencimiento.plusDays(10), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.situacion().periodosExtemporaneos()).isEqualTo(2);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("0");
        }
    }

    /**
     * D-04 (plan 07-oct §2.3, §2.7 C17): el contrato en venta cobra la misma sanción
     * (630 × 2% × min(n, 4)) por REFRENDO (→ RX), FINIQUITO (→ FX) y REFRENDO_PARCIAL con
     * n ∈ {5, 7}, y la sanción entra en la base del IVA en los cuatro caminos.
     *
     * <p>Fixture C17: S = 630, porcInteres 2.90 + porcAlmacen 0.60 → 22.05 por periodo;
     * FC 16-ago-2026, FV 13-sep-2026, operación 07-oct-2026 → 24 días de atraso,
     * 8 periodos transcurridos (4 normales + 4 extemporáneos), ceil(24/7) = 4 semanas.</p>
     */
    @Nested
    class D04SancionVisibleEnTodosLosCaminos {

        private final LocalDate fechaContrato = d(2026, 8, 16);
        private final LocalDate vencimiento = d(2026, 9, 13);
        private final LocalDate hoy = d(2026, 10, 7);
        private final BigDecimal sancionEsperada = new BigDecimal("50.40"); // 630 × 2% × 4

        private Contrato c17() {
            Contrato c = contrato("630.00", fechaContrato, vencimiento);
            c.setEstatus(EstatusContrato.EN_VENTA);
            return c;
        }

        /** La sanción tiene que entrar en la base del IVA, no quedarse colgada aparte. */
        private void assertSancionEnBaseIva(CotizacionMovimiento cot) {
            DesgloseCobro d = cot.desglose();
            BigDecimal baseEsperada = d.interes().add(d.almacen()).subtract(d.descuento()).add(d.sancion());
            assertThat(d.baseIva())
                    .as("baseIva debe incluir la sanción (interés + almacén − descuento + sanción)")
                    .isEqualByComparingTo(baseEsperada);
            // IVA = baseIva × 16% truncado DOWN a 2 decimales (RN-10).
            BigDecimal ivaEsperado = baseEsperada.multiply(new BigDecimal("16.00"))
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.DOWN);
            assertThat(d.iva()).isEqualByComparingTo(ivaEsperado);
        }

        @Test
        void refrendoEnVenta_resuelveARX_sancionEsSaldoPor2PorCuatroSemanas() {
            CotizacionMovimiento cot = motor.cotizar(c17(), params("2.90"),
                    TipoOperacion.REFRENDO, hoy, null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RX);
            assertThat(cot.periodosAplicados()).isEqualTo(8);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(4);
            assertThat(cot.desglose().semanasVencidas()).isEqualTo(4);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo(sancionEsperada);
            assertSancionEnBaseIva(cot);
        }

        @Test
        void finiquitoEnVenta_resuelveAFX_sancionIgualQueElRX() {
            CotizacionMovimiento cot = motor.cotizar(c17(), params("2.90"),
                    TipoOperacion.FINIQUITO, hoy, null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.FX);
            assertThat(cot.periodosAplicados()).isEqualTo(8);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(4);
            assertThat(cot.desglose().semanasVencidas()).isEqualTo(4);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo(sancionEsperada);
            assertSancionEnBaseIva(cot);
            assertThat(cot.capital()).isEqualByComparingTo("630.00");
        }

        @Test
        void parcialEnVentaConSieteSemanas_resuelveARPX_cubreLosCuatroExtemporaneos() {
            CotizacionMovimiento cot = motor.cotizar(c17(), params("2.90"),
                    TipoOperacion.REFRENDO_PARCIAL, hoy, 7, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPX);
            assertThat(cot.periodosAplicados()).isEqualTo(7);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(4);
            assertThat(cot.desglose().semanasVencidas()).isEqualTo(4);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo(sancionEsperada);
            assertSancionEnBaseIva(cot);
        }

        @Test
        void parcialEnVentaConCincoSemanas_resuelveARPX_mismaSancionQueElRX() {
            CotizacionMovimiento cot = motor.cotizar(c17(), params("2.90"),
                    TipoOperacion.REFRENDO_PARCIAL, hoy, 5, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RPX);
            assertThat(cot.periodosAplicados()).isEqualTo(5);
            assertThat(cot.periodosExtemporaneosAplicados()).isEqualTo(4);
            assertThat(cot.desglose().semanasVencidas()).isEqualTo(4);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo(sancionEsperada);
            assertSancionEnBaseIva(cot);
        }

        /** Cross-check: los cuatro caminos cobran exactamente la misma sanción por semana. */
        @Test
        void rxFxYRpxCobranLaMismaSancion_mismasSemanas() {
            Contrato c = c17();
            ParametrosCalculo p = params("2.90");

            BigDecimal sRx  = motor.cotizar(c, p, TipoOperacion.REFRENDO,         hoy, null, null).desglose().sancion();
            BigDecimal sFx  = motor.cotizar(c, p, TipoOperacion.FINIQUITO,        hoy, null, null).desglose().sancion();
            BigDecimal sRp7 = motor.cotizar(c, p, TipoOperacion.REFRENDO_PARCIAL, hoy, 7,    null).desglose().sancion();
            BigDecimal sRp5 = motor.cotizar(c, p, TipoOperacion.REFRENDO_PARCIAL, hoy, 5,    null).desglose().sancion();

            assertThat(sRx).isEqualByComparingTo(sancionEsperada);
            assertThat(sFx).isEqualByComparingTo(sancionEsperada);
            assertThat(sRp7).isEqualByComparingTo(sancionEsperada);
            assertThat(sRp5).isEqualByComparingTo(sancionEsperada);
        }

        /**
         * D-04 (cotización): aunque la sanción pueda ser 0 (contrato vigente / en gracia), la
         * cotización debe entregar el porcentaje y las semanas al frontend para que el modal
         * pueda imprimir siempre la línea "Sanción extemporánea (p% × n sem.)".
         */
        @Test
        void vigenteSinSancion_porcentajeYSemanasSiguenEnLaCotizacion() {
            // Contrato vigente, 1 periodo transcurrido, sin atraso: sanción 0 pero %Sanción = 2 y semanas = 0.
            Contrato c = contrato("1000.00", d(2026, 10, 1), d(2026, 10, 29));
            CotizacionMovimiento cot = motor.cotizar(c, params("1.13"),
                    TipoOperacion.REFRENDO, d(2026, 10, 7), null, null);

            assertThat(cot.tipoMovimiento()).isEqualTo(TipoMovimiento.RF);
            assertThat(cot.desglose().sancion()).isEqualByComparingTo("0");
            assertThat(cot.desglose().semanasVencidas()).isZero();
            assertThat(cot.parametros().porcSancionSemanal()).isEqualByComparingTo("2.00");
        }

        /**
         * D-14: con el gate {@code aplicarSancion} removido del motor, un contrato con
         * {@code aplicarSancion=false} (contrato pre-SANC-04, o config vieja) debe cobrar la
         * misma sanción que uno con {@code aplicarSancion=true} del mismo porcentaje. El toggle
         * sigue existiendo en {@link ParametrosCalculo} por compatibilidad pero no influye en el
         * cálculo. Antes del fix esto hubiera dado {@code sancion=0} y divergencia PROFECO
         * (PDF 0% vs caja 2%).
         */
        @Test
        void rxConAplicarSancionFalse_cobraLaMismaSancionQueConTrue() {
            // Contrato de C17 (venta: 4 periodos + 4 ext). Misma configuración salvo el toggle.
            ParametrosCalculo conGate = new ParametrosCalculo(
                    new BigDecimal("2.90"), new BigDecimal("0.60"), new BigDecimal("1.00"),
                    new BigDecimal("2.00"), 2, true,
                    new BigDecimal("16.00"), BigDecimal.ZERO);
            ParametrosCalculo sinGate = new ParametrosCalculo(
                    new BigDecimal("2.90"), new BigDecimal("0.60"), new BigDecimal("1.00"),
                    new BigDecimal("2.00"), 2, false,   // ← aplicarSancion = false
                    new BigDecimal("16.00"), BigDecimal.ZERO);

            CotizacionMovimiento conGateCot = motor.cotizar(c17(), conGate,
                    TipoOperacion.REFRENDO, hoy, null, null);
            CotizacionMovimiento sinGateCot = motor.cotizar(c17(), sinGate,
                    TipoOperacion.REFRENDO, hoy, null, null);

            // El RX es el que cuenta: 630 × 2% × 4 sem = 50.40, igual en ambos.
            assertThat(conGateCot.desglose().sancion()).isEqualByComparingTo("50.40");
            assertThat(sinGateCot.desglose().sancion()).isEqualByComparingTo("50.40");
            assertThat(sinGateCot.desglose().sancion())
                    .isEqualByComparingTo(conGateCot.desglose().sancion());
            // Y la base del IVA incluye la sanción en ambos (no se "oculta" ni se "resta").
            assertThat(sinGateCot.desglose().baseIva())
                    .isEqualByComparingTo(conGateCot.desglose().baseIva());
        }
    }
}
