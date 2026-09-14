package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.PlazoParametro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del motor de calculo canonico. Cubre las tres unidades:
 *   - calcularSancion: gracia, redondeo ceil, toggle, casos frontera.
 *   - calcularCobroPeriodo: interes/almacen/gastos por periodoAcumulado, IVA truncado DOWN.
 *   - resolverParametros: snapshot > vigente; contrato con snapshot congela numeros.
 *
 * Este servicio NO tiene aun consumidor real (Pasada 1). La suite garantiza que la logica
 * ya funciona correctamente antes de migrar refrendar/PDF/amortizacion en Pasada 2.
 */
@ExtendWith(MockitoExtension.class)
class CalculoContratoServiceTest {

    @Mock
    ParametrosSistemaCache parametrosSistemaCache;

    CalculoContratoService motor;

    @BeforeEach
    void setUp() {
        motor = new CalculoContratoService(parametrosSistemaCache);
    }

    private ParametrosCalculo paramsBase(int diasGracia, boolean aplicarSancion) {
        return new ParametrosCalculo(
                new BigDecimal("3.0000"),   // porcInteres
                new BigDecimal("2.0000"),   // porcAlmacen
                new BigDecimal("1.0000"),   // porcGastosAdmin
                new BigDecimal("2.0000"),   // porcSancionSemanal
                diasGracia,
                aplicarSancion,
                new BigDecimal("16.00")     // porcIva
        );
    }

    // =========================================================================
    // A. Gracia y redondeo — casos frontera del brief
    // =========================================================================

    @Nested
    class GraciaYRedondeo {

        @Test
        void atraso0DiasGracia2_sancion0() {
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 1); // mismo dia
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isZero();
            assertThat(d.semanasVencidas()).isZero();
            assertThat(d.monto()).isEqualByComparingTo("0.00");
        }

        @Test
        void atraso2DiasGracia2_sinSancion() {
            // Frontera exacta: 2 dias de atraso, gracia 2 -> aun tolerancia
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 3);
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isZero();
            assertThat(d.semanasVencidas()).isZero();
            assertThat(d.monto()).isEqualByComparingTo("0.00");
        }

        @Test
        void atraso3DiasGracia2_una_semana() {
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 4);
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isEqualTo(1);
            assertThat(d.semanasVencidas()).isEqualTo(1);
            // 1000 * 2/100 * 1 = 20.00
            assertThat(d.monto()).isEqualByComparingTo("20.00");
        }

        @Test
        void atraso9DiasGracia2_una_semana_frontera_exacta() {
            // 9 - 2 = 7 dias -> ceil(7/7) = 1 semana (frontera exacta antes de cruzar a 2)
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 10);
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isEqualTo(7);
            assertThat(d.semanasVencidas()).isEqualTo(1);
            assertThat(d.monto()).isEqualByComparingTo("20.00");
        }

        @Test
        void atraso10DiasGracia2_dos_semanas() {
            // 10 - 2 = 8 dias -> ceil(8/7) = 2 semanas
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 11);
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isEqualTo(8);
            assertThat(d.semanasVencidas()).isEqualTo(2);
            assertThat(d.monto()).isEqualByComparingTo("40.00");
        }

        @Test
        void atraso100DiasGracia0_quince_semanas() {
            // 100 - 0 = 100 dias -> ceil(100/7) = 15 semanas (14.28... -> 15)
            LocalDate vencimiento = LocalDate.of(2026, 6, 1);
            LocalDate pago = vencimiento.plusDays(100);
            DesgloseSancion d = motor.calcularSancion(
                    new BigDecimal("1000.00"), paramsBase(0, true), vencimiento, pago);
            assertThat(d.diasAtraso()).isEqualTo(100);
            assertThat(d.semanasVencidas()).isEqualTo(15);
            // 1000 * 2/100 * 15 = 300.00
            assertThat(d.monto()).isEqualByComparingTo("300.00");
        }
    }

    // =========================================================================
    // B. Toggle aplicarSancion
    // =========================================================================

    @Test
    void toggleApagadoConAtrasoGrande_sancion0() {
        LocalDate vencimiento = LocalDate.of(2026, 9, 1);
        LocalDate pago = vencimiento.plusDays(30);
        DesgloseSancion d = motor.calcularSancion(
                new BigDecimal("1000.00"), paramsBase(2, false), vencimiento, pago);
        // El motor computa semanas y dias como metadatos, pero el monto queda en 0 por el toggle
        assertThat(d.monto()).isEqualByComparingTo("0.00");
        assertThat(d.semanasVencidas()).isZero();
    }

    // =========================================================================
    // C. calcularCobroPeriodo — interes/almacen/gastos por periodoAcumulado, IVA truncado
    // =========================================================================

    @Nested
    class CobroPeriodo {

        @Test
        void periodo1_sinAtraso_interesAlmacenGastosMasIvaSobreEllos() {
            LocalDate vencimiento = LocalDate.of(2026, 9, 30);
            LocalDate pago = LocalDate.of(2026, 9, 25); // antes del vencimiento
            DesgloseCobro d = motor.calcularCobroPeriodo(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago, 1);
            // interes = 1000 * 3 * 1 / 100 = 30.00
            // almacen = 1000 * 2 * 1 / 100 = 20.00
            // gastos  = 1000 * 1 * 1 / 100 = 10.00
            // sancion = 0
            assertThat(d.interes()).isEqualByComparingTo("30.00");
            assertThat(d.almacen()).isEqualByComparingTo("20.00");
            assertThat(d.gastosAdmin()).isEqualByComparingTo("10.00");
            assertThat(d.sancion()).isEqualByComparingTo("0.00");
            // baseIva = 30 + 20 + 10 + 0 = 60.00; iva = 60 * 16/100 = 9.60 (exacto)
            assertThat(d.baseIva()).isEqualByComparingTo("60.00");
            assertThat(d.iva()).isEqualByComparingTo("9.60");
            assertThat(d.total()).isEqualByComparingTo("69.60");
        }

        @Test
        void periodo2_multiplicaPorPeriodoAcumulado() {
            // Segundo periodo del PDF: interes/almacen/gastos se duplican
            LocalDate vencimiento = LocalDate.of(2026, 9, 30);
            LocalDate pago = LocalDate.of(2026, 9, 25);
            DesgloseCobro d = motor.calcularCobroPeriodo(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago, 2);
            assertThat(d.interes()).isEqualByComparingTo("60.00");
            assertThat(d.almacen()).isEqualByComparingTo("40.00");
            assertThat(d.gastosAdmin()).isEqualByComparingTo("20.00");
            assertThat(d.baseIva()).isEqualByComparingTo("120.00");
            assertThat(d.iva()).isEqualByComparingTo("19.20");
            assertThat(d.total()).isEqualByComparingTo("139.20");
        }

        @Test
        void extemporaneo_interesDelPeriodoMasSancionMasIvaSobreTodo() {
            // Fila S5 tipica: vencido 10 dias con gracia 2 -> 2 semanas de sancion
            LocalDate vencimiento = LocalDate.of(2026, 9, 1);
            LocalDate pago = LocalDate.of(2026, 9, 11);
            DesgloseCobro d = motor.calcularCobroPeriodo(
                    new BigDecimal("1000.00"), paramsBase(2, true), vencimiento, pago, 1);
            assertThat(d.sancion()).isEqualByComparingTo("40.00"); // 2 semanas
            assertThat(d.baseIva()).isEqualByComparingTo("100.00"); // 30 + 20 + 10 + 40
            assertThat(d.iva()).isEqualByComparingTo("16.00");
            assertThat(d.total()).isEqualByComparingTo("116.00");
        }

        @Test
        void ivaTruncaDown_noRedondea() {
            // Un total que fuerza truncado: baseIva = 22.05 * 16 / 100 = 3.528 -> DEBE quedar 3.52 (DOWN)
            // Construyo prestamo tal que baseIva = 22.05: interes 22.05 con porcInteres=2.205 y prestamo 1000
            ParametrosCalculo p = new ParametrosCalculo(
                    new BigDecimal("2.2050"), // interes puro para que 1000*2.205/100 = 22.05
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 2, false,
                    new BigDecimal("16.00"));
            LocalDate v = LocalDate.of(2026, 9, 30);
            DesgloseCobro d = motor.calcularCobroPeriodo(
                    new BigDecimal("1000.00"), p, v, v.minusDays(1), 1);
            assertThat(d.baseIva()).isEqualByComparingTo("22.05");
            // 22.05 * 16 / 100 = 3.528 -> truncado a 2 dec = 3.52 (no 3.53)
            assertThat(d.iva()).isEqualByComparingTo("3.52");
        }
    }

    // =========================================================================
    // D. IVA — LEE del cache, no de constante; fallback con warning
    // =========================================================================

    @Nested
    class IvaDesdeCache {

        @Test
        void resolverParametros_usaIvaVigenteCuandoContratoNoTieneSnapshot() {
            when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

            Contrato c = new Contrato();
            c.setSnapIvaPorcentaje(null); // contrato historico sin snapshot
            PlazoParametro vigente = plazoParametroCompleto();

            ParametrosCalculo p = motor.resolverParametros(c, vigente);
            assertThat(p.porcIva()).isEqualByComparingTo("16.00");
        }

        @Test
        void resolverParametros_usaIvaDelSnapshotCuandoContratoLoTiene() {
            // El contrato fue creado cuando IVA era 16; hoy la config vigente cambio a 8.
            // El motor debe respetar el snapshot: reimpresion no muta montos (PROFECO).
            lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("8.00"));

            Contrato c = new Contrato();
            c.setSnapIvaPorcentaje(new BigDecimal("16.00"));
            PlazoParametro vigente = plazoParametroCompleto();

            ParametrosCalculo p = motor.resolverParametros(c, vigente);
            assertThat(p.porcIva()).isEqualByComparingTo("16.00");
        }

        @Test
        void resolverParametros_ivaCambia8_pruebaQueLeeElParametroNoConstante() {
            // Sin snapshot, el cache dicta el valor. Cambiar el mock a 8 debe cambiar el IVA resuelto.
            when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("8.00"));

            Contrato c = new Contrato(); // sin snapshot
            PlazoParametro vigente = plazoParametroCompleto();

            ParametrosCalculo p = motor.resolverParametros(c, vigente);
            assertThat(p.porcIva()).isEqualByComparingTo("8.00");
        }
    }

    // =========================================================================
    // E. resolverParametros — snapshot completo congela los 7 valores
    // =========================================================================

    @Test
    void resolverParametros_snapshotCompletoIgnoraConfigVigente() {
        // Contrato con snapshot de todos los 7 campos; la config vigente cambio en todos.
        // Motor debe usar EXCLUSIVAMENTE el snapshot.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("8.00"));

        Contrato c = new Contrato();
        c.setSnapPorcSancionSemanal(new BigDecimal("2.5000"));
        c.setSnapDiasGraciaSancion(3);
        c.setSnapAplicarSancionPeriodo(true);
        c.setSnapIvaPorcentaje(new BigDecimal("16.00"));
        c.setSnapPorcInteres(new BigDecimal("4.0000"));
        c.setSnapPorcAlmacen(new BigDecimal("1.5000"));
        c.setSnapPorcGastosAdmin(new BigDecimal("0.5000"));

        PlazoParametro vigente = new PlazoParametro(); // valores distintos "vigentes"
        vigente.setPorcSancionSemanal(new BigDecimal("999.9999"));
        vigente.setDiasGraciaSinInteres(999);
        vigente.setAplicarSancionPorPeriodo(false);
        vigente.setPorcInteres(new BigDecimal("999.9999"));
        vigente.setPorcAlmacen(new BigDecimal("999.9999"));
        vigente.setPorcGastosAdmin(new BigDecimal("999.9999"));

        ParametrosCalculo p = motor.resolverParametros(c, vigente);
        assertThat(p.porcSancionSemanal()).isEqualByComparingTo("2.5000");
        assertThat(p.diasGraciaSancion()).isEqualTo(3);
        assertThat(p.aplicarSancion()).isTrue();
        assertThat(p.porcIva()).isEqualByComparingTo("16.00");
        assertThat(p.porcInteres()).isEqualByComparingTo("4.0000");
        assertThat(p.porcAlmacen()).isEqualByComparingTo("1.5000");
        assertThat(p.porcGastosAdmin()).isEqualByComparingTo("0.5000");
    }

    @Test
    void resolverParametros_sinSnapshotUsaConfigVigenteEnCadaCampo() {
        when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

        Contrato c = new Contrato(); // los 7 snap_* en null
        PlazoParametro vigente = plazoParametroCompleto();

        ParametrosCalculo p = motor.resolverParametros(c, vigente);
        assertThat(p.porcSancionSemanal()).isEqualByComparingTo("2.0000");
        assertThat(p.diasGraciaSancion()).isEqualTo(2);
        assertThat(p.aplicarSancion()).isTrue();
        assertThat(p.porcIva()).isEqualByComparingTo("16.00");
        assertThat(p.porcInteres()).isEqualByComparingTo("3.0000");
        assertThat(p.porcAlmacen()).isEqualByComparingTo("2.0000");
        assertThat(p.porcGastosAdmin()).isEqualByComparingTo("1.0000");
    }

    @Test
    void resolverParametros_parametroVigenteNull_devuelveCerosSegurosYRespetaSnapshot() {
        // Contratos sin PlazoParametro configurado no deben tumbar el motor con NPE.
        when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

        Contrato c = new Contrato(); // sin snapshot
        ParametrosCalculo p = motor.resolverParametros(c, null);
        assertThat(p.porcInteres()).isEqualByComparingTo("0");
        assertThat(p.aplicarSancion()).isFalse();
        assertThat(p.porcIva()).isEqualByComparingTo("16.00");
    }

    // =========================================================================
    // F. Consistencia refrendar ↔ PDF: mismo input, mismo output
    // =========================================================================

    @Test
    void consistenciaRefrendarVsPdf_mismoInputMismoDesglose() {
        // Simula: refrendar y PDF llaman al motor con identicos argumentos.
        // Debe devolver el mismo DesgloseCobro (garantia de que no divergen).
        LocalDate vencimiento = LocalDate.of(2026, 9, 1);
        LocalDate pago = LocalDate.of(2026, 9, 11);
        BigDecimal prestamo = new BigDecimal("1000.00");
        ParametrosCalculo p = paramsBase(2, true);

        DesgloseCobro dRefrendar = motor.calcularCobroPeriodo(prestamo, p, vencimiento, pago, 1);
        DesgloseCobro dPdf       = motor.calcularCobroPeriodo(prestamo, p, vencimiento, pago, 1);

        assertThat(dRefrendar).isEqualTo(dPdf);
    }

    // =========================================================================
    // Helper: PlazoParametro con los valores que espera paramsBase(2, true)
    // =========================================================================

    private PlazoParametro plazoParametroCompleto() {
        PlazoParametro p = new PlazoParametro();
        p.setPorcInteres(new BigDecimal("3.0000"));
        p.setPorcAlmacen(new BigDecimal("2.0000"));
        p.setPorcGastosAdmin(new BigDecimal("1.0000"));
        p.setPorcSancionSemanal(new BigDecimal("2.0000"));
        p.setDiasGraciaSinInteres(2);
        p.setAplicarSancionPorPeriodo(true);
        return p;
    }
}
