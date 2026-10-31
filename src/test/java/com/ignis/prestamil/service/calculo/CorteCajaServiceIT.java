package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.service.ResolutorRenglonCorte;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Motor del corte de caja (CAJ-05) contra H2, con las consultas reales del resolutor y de los traspasos.
 * El caso principal reproduce el corte del 17/09/2026 de Pinotepa 2 (sección 5.4 de
 * PLAN-VENTAS-CAJA-REPORTES), con ±0.02 donde COCAE redondea distinto.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CorteCajaService.class, ResolutorRenglonCorte.class, SaldoArranqueCajaProvider.class})
class CorteCajaServiceIT {

    private static final int SUCURSAL = CorteCajaSemilla.SUCURSAL;
    private static final LocalDate DIA_5_4 = CorteCajaSemilla.DIA_5_4;
    /** Tolerancia de 5.4 para las cifras que COCAE calcula con más decimales. */
    private static final BigDecimal COCAE = new BigDecimal("0.02");

    @Autowired CorteCajaService corteCajaService;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired EntityManager em;

    private CorteCajaSemilla semilla;

    @BeforeEach
    void sembrar() {
        semilla = new CorteCajaSemilla(em, configuracionRepository);
    }

    @Test
    void corteDel17deSeptiembre_reproduceLaSeccion5_4() {
        semilla.sembrar17deSeptiembre();

        CorteCajaResultado corte = corteCajaService.calcular(SUCURSAL, DIA_5_4);

        assertThat(corte.valido()).isTrue();
        assertThat(corte.renglones()).extracting(CorteCajaResultado.Renglon::renglon)
                .containsExactly(RenglonCorte.values());
        // Bloque superior, renglón por renglón
        assertEfectivo(corte, RenglonCorte.DOTACIONES, "18861.85");
        assertEfectivo(corte, RenglonCorte.EMPENOS, "32370.00");
        assertEfectivo(corte, RenglonCorte.INDEMNIZACIONES, "0.00");
        assertEfectivo(corte, RenglonCorte.DEVOLUCIONES, "0.00");
        assertEfectivo(corte, RenglonCorte.DEMASIAS, "0.00");
        assertEfectivo(corte, RenglonCorte.ABONOS_PARCIALES, "0.00");
        assertEfectivo(corte, RenglonCorte.ABONOS_CAPITAL, "10489.00");
        assertEfectivo(corte, RenglonCorte.APARTADOS, "0.00");
        assertEfectivo(corte, RenglonCorte.FINIQUITOS, "25017.29");
        assertEfectivo(corte, RenglonCorte.FINIQUITOS_EXTEMPORANEOS, "0.00");
        assertEfectivo(corte, RenglonCorte.REFRENDOS, "15387.26");
        assertEfectivo(corte, RenglonCorte.REFRENDOS_EXTEMPORANEOS, "4153.24");
        assertEfectivo(corte, RenglonCorte.REPOSICIONES, "0.00");
        assertEfectivo(corte, RenglonCorte.VENTAS, "2350.87");
        assertEfectivo(corte, RenglonCorte.RECOLECCION, "43889.50");

        // Columnas: COCAE imprime 76,259.50 de ingresos; la suma exacta de sus renglones es 76,259.51
        assertThat(corte.ingresos().efectivo()).isCloseTo(new BigDecimal("76259.50"), within(COCAE));
        assertThat(corte.ingresos().efectivo()).isEqualByComparingTo("76259.51");
        assertThat(corte.egresos().efectivo()).isEqualByComparingTo("76259.50");
        assertThat(corte.ingresos().tarjeta()).isEqualByComparingTo("0.00");

        // Bloque inferior (RC-08)
        assertThat(corte.saldoAnterior()).isEqualByComparingTo("41013.11");
        assertThat(corte.ministraciones()).isEqualByComparingTo("0.00");
        assertThat(corte.concentraciones()).isEqualByComparingTo("1000.00");
        assertThat(corte.boveda()).isEqualByComparingTo("40013.11");
        assertThat(corte.saldoFinal()).isCloseTo(new BigDecimal("65040.77"), within(COCAE));
        assertThat(corte.saldoFinal()).isEqualByComparingTo("65040.76");

        // Arqueo (RC-07): 18,861.85 + 57,397.65 − 32,370.00 − 43,889.50 = 0.00 en COCAE
        assertThat(corte.dotaciones()).isEqualByComparingTo("18861.85");
        assertThat(corte.ingresosEfectivo()).isCloseTo(new BigDecimal("57397.65"), within(COCAE));
        assertThat(corte.egresosEfectivo()).isEqualByComparingTo("32370.00");
        assertThat(corte.recolecciones()).isEqualByComparingTo("43889.50");
        assertThat(corte.diferencia()).isCloseTo(BigDecimal.ZERO, within(COCAE));
    }

    @Test
    void cobroMixto_laTarjetaSeVeAparteYNoEntraAlArqueo() {
        LocalDate dia = LocalDate.of(2026, 10, 1);
        semilla.arranque(dia, "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", dia);
        // Refrendo de 1,000: 600 en efectivo y 400 con tarjeta (RC-06)
        semilla.flujo(ConceptoCaja.REFRENDO, "600.00", "400.00", null, null, dia.atTime(11, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "1600.00", dia);
        em.flush();

        CorteCajaResultado corte = corteCajaService.calcular(SUCURSAL, dia);

        CorteCajaResultado.Renglon refrendos = corte.renglon(RenglonCorte.REFRENDOS);
        assertThat(refrendos.efectivo()).isEqualByComparingTo("600.00");
        assertThat(refrendos.tarjeta()).isEqualByComparingTo("400.00");
        assertThat(refrendos.total()).isEqualByComparingTo("1000.00");
        assertThat(corte.ingresos().tarjeta()).isEqualByComparingTo("400.00");
        assertThat(corte.ingresosEfectivo()).isEqualByComparingTo("600.00");
        // 1,000 + 600 − 0 − 1,600: la gaveta cuadra sin los 400 de la terminal
        assertThat(corte.diferencia()).isEqualByComparingTo("0.00");
        assertThat(corte.indicador()).isNull();
        assertThat(corte.saldoFinal()).isEqualByComparingTo("10600.00");
    }

    @Test
    void diaSinMovimientos_todoEnCeroYArrastraElSaldo() {
        LocalDate dia = LocalDate.of(2026, 10, 2);
        semilla.arranque(dia.minusDays(2), "5000.00");
        semilla.traspaso(TipoTraspasoCaja.MINISTRACION, "1000.00", dia.minusDays(1));
        em.flush();

        CorteCajaResultado corte = corteCajaService.calcular(SUCURSAL, dia);

        assertThat(corte.valido()).isTrue();
        assertThat(corte.renglones()).hasSize(RenglonCorte.values().length)
                .allSatisfy(r -> {
                    assertThat(r.efectivo()).isEqualByComparingTo("0.00");
                    assertThat(r.tarjeta()).isEqualByComparingTo("0.00");
                });
        assertThat(corte.saldoAnterior()).isEqualByComparingTo("6000.00");
        assertThat(corte.boveda()).isEqualByComparingTo("6000.00");
        assertThat(corte.saldoFinal()).isEqualByComparingTo("6000.00");
        assertThat(corte.diferencia()).isEqualByComparingTo("0.00");
        assertThat(corte.indicador()).isNull();
    }

    @Test
    void rango_arrastraElSaldoFinalYCoincideConElCorteDeCadaDia() {
        LocalDate d1 = LocalDate.of(2026, 10, 5);
        LocalDate d2 = d1.plusDays(1);
        LocalDate d3 = d1.plusDays(2);
        semilla.arranque(d1, "1000.00");
        // Día 1: venta en efectivo, todo se recolecta
        semilla.traspaso(TipoTraspasoCaja.MINISTRACION, "5000.00", d1);
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "2000.00", d1);
        semilla.flujo(ConceptoCaja.VENTA_VITRINA, "500.00", "0.00", OrigenCaja.VENTA, 7L, d1.atTime(12, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "2500.00", d1);
        // Día 2: se cancela la venta de ayer; resta en VENTAS de hoy (Q-C1) y la gaveta queda con 100 de más
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", d2);
        semilla.flujo(ConceptoCaja.CANCELACION_VENTA, "500.00", "0.00", OrigenCaja.CANCELACION_VENTA, 3L, d2.atTime(9, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "400.00", d2);
        em.flush();

        List<CorteCajaResultado> rango = corteCajaService.calcularRango(SUCURSAL, d1.minusDays(1), d3);

        assertThat(rango).extracting(CorteCajaResultado::fecha).containsExactly(d1.minusDays(1), d1, d2, d3);
        // Antes del arranque: aviso, no cifras
        assertThat(rango.get(0).valido()).isFalse();
        assertThat(rango.get(0).aviso()).contains("05/10/2026");
        assertThat(rango.get(0).renglones()).isEmpty();
        assertThat(rango.get(0).saldoFinal()).isNull();

        CorteCajaResultado c1 = rango.get(1);
        CorteCajaResultado c2 = rango.get(2);
        CorteCajaResultado c3 = rango.get(3);
        // 1,000 + 5,000 − 2,000 + 2,500
        assertThat(c1.saldoFinal()).isEqualByComparingTo("6500.00");
        assertThat(c1.diferencia()).isEqualByComparingTo("0.00");
        assertThat(c2.saldoAnterior()).isEqualByComparingTo(c1.saldoFinal());
        assertThat(c2.renglon(RenglonCorte.VENTAS).efectivo()).isEqualByComparingTo("-500.00");
        // 1,000 + (−500) − 0 − 400 = 100 que siguen en la gaveta
        assertThat(c2.diferencia()).isEqualByComparingTo("100.00");
        assertThat(c2.indicador()).isEqualTo(CorteCajaResultado.Indicador.F);
        assertThat(c2.saldoFinal()).isEqualByComparingTo("5900.00");
        assertThat(c3.saldoAnterior()).isEqualByComparingTo("5900.00");
        assertThat(c3.saldoFinal()).isEqualByComparingTo("5900.00");

        // Mismo resultado que pedir cada día por separado (RC-09: un solo cálculo)
        for (CorteCajaResultado delRango : rango.subList(1, 4)) {
            assertThat(corteCajaService.calcular(SUCURSAL, delRango.fecha()))
                    .usingRecursiveComparison().isEqualTo(delRango);
        }
    }

    @Test
    void sobrante_cuandoSeRecolectaMasDeLoQueDebeHaber() {
        LocalDate dia = LocalDate.of(2026, 10, 8);
        semilla.arranque(dia, "5000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", dia);
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "1000.03", dia);
        em.flush();

        CorteCajaResultado corte = corteCajaService.calcular(SUCURSAL, dia);

        assertThat(corte.diferencia()).isEqualByComparingTo("-0.03");
        assertThat(corte.indicador()).isEqualTo(CorteCajaResultado.Indicador.S);
    }

    @Test
    void sinSaldoDeArranque_avisoYSinCifras() {
        CorteCajaResultado corte = corteCajaService.calcular(SUCURSAL, DIA_5_4);

        assertThat(corte.valido()).isFalse();
        assertThat(corte.aviso()).contains("saldo de arranque");
        assertThat(corte.renglones()).isEmpty();
        assertThat(corte.diferencia()).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private static void assertEfectivo(CorteCajaResultado corte, RenglonCorte renglon, String efectivo) {
        assertThat(corte.renglon(renglon).efectivo()).as(renglon.name()).isEqualByComparingTo(efectivo);
    }
}
