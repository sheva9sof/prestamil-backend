package com.ignis.prestamil.service;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Renglón del corte de cada flujo de caja (CAJ-01d, sección 3 de PLAN-CORTE-CAJA): RC dividido en capital
 * y refrendo (regla 2), cancelaciones que restan del renglón de la operación original (Q-C1) y cobertura de
 * todos los tipos de movimiento.
 */
@ExtendWith(MockitoExtension.class)
class ResolutorRenglonCorteTest {

    @Mock MovimientoCajaRepository movimientoCajaRepository;
    @Mock MovimientoContratoRepository movimientoContratoRepository;

    ResolutorRenglonCorte resolutor;

    /** Movimientos de contrato que devuelve el repositorio, por id. */
    private final List<MovimientoContrato> contratos = new ArrayList<>();
    private long siguienteId = 1;

    @BeforeEach
    void setUp() {
        resolutor = new ResolutorRenglonCorte(movimientoCajaRepository, movimientoContratoRepository);
        lenient().when(movimientoContratoRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return contratos.stream().filter(m -> ids.contains(m.getId())).toList();
        });
    }

    // =========================================================================
    // Regla 2: división de RC
    // =========================================================================

    @Test
    void rcDeMilDeCapitalMasInteresEIva_mitadConTarjeta_seRepartePorPartes() {
        // $1,000 de capital + $120 de interés + $19.20 de IVA = $1,139.20; mitad con tarjeta, mitad en efectivo
        MovimientoContrato rc = contrato(TipoMovimiento.RC, "1000.00", "0.00");
        MovimientoCaja cobro = flujo(ConceptoCaja.ABONO_CAPITAL, "569.60", "569.60", rc);

        Map<RenglonCorte, ParteCorte> totales = resolutor.totalesPorRenglon(List.of(cobro));

        assertParte(totales.get(RenglonCorte.ABONOS_CAPITAL), "500.00", "500.00");
        assertParte(totales.get(RenglonCorte.REFRENDOS), "69.60", "69.60");
        assertThat(totales.get(RenglonCorte.ABONOS_CAPITAL).total().add(totales.get(RenglonCorte.REFRENDOS).total()))
                .isEqualByComparingTo("1139.20");
        assertParte(totales.get(RenglonCorte.REFRENDOS_EXTEMPORANEOS), "0", "0");
    }

    @Test
    void rcConSancion_laParteDeRefrendoVaAExtemporaneos() {
        MovimientoContrato rc = contrato(TipoMovimiento.RC, "1000.00", "45.00");
        MovimientoCaja cobro = flujo(ConceptoCaja.ABONO_CAPITAL, "1217.40", "0.00", rc);

        Map<RenglonCorte, ParteCorte> totales = resolutor.totalesPorRenglon(List.of(cobro));

        assertParte(totales.get(RenglonCorte.ABONOS_CAPITAL), "1000.00", "0.00");
        assertParte(totales.get(RenglonCorte.REFRENDOS_EXTEMPORANEOS), "217.40", "0.00");
        assertParte(totales.get(RenglonCorte.REFRENDOS), "0", "0");
    }

    @Test
    void divisionRc_elRedondeoVaALaParteDeCapital() {
        // Refrendo 500 × 333.33 / 1000 = 166.665 → 166.67; el capital se queda con 166.66
        List<ParteCorte> partes = ResolutorRenglonCorte.dividirAbonoCapital(
                new BigDecimal("333.33"), new BigDecimal("666.67"), new BigDecimal("500.00"), false);

        assertThat(partes.get(0).renglon()).isEqualTo(RenglonCorte.ABONOS_CAPITAL);
        assertParte(partes.get(0), "166.66", "333.34");
        assertThat(partes.get(1).renglon()).isEqualTo(RenglonCorte.REFRENDOS);
        assertParte(partes.get(1), "166.67", "333.33");
    }

    @ParameterizedTest(name = "efectivo={0} tarjeta={1} capital={2}")
    @CsvSource({
            "333.33, 666.67, 500.00",
            "0.01, 0.02, 0.01",
            "0.02, 0.01, 0.01",
            "100.00, 0.00, 33.33",
            "0.00, 250.00, 200.00",
            "1139.21, 0.00, 1000.00",
            "10.00, 0.01, 5.00",
            "777.77, 333.33, 1000.00",
            "1.00, 1.00, 0.00",
            "1.00, 1.00, 2.00",
            "0.05, 1234.56, 999.99"
    })
    void divisionRc_lasSumasCuadranAlCentavo(String efectivo, String tarjeta, String capital) {
        BigDecimal e = new BigDecimal(efectivo);
        BigDecimal t = new BigDecimal(tarjeta);
        BigDecimal c = new BigDecimal(capital);

        List<ParteCorte> partes = ResolutorRenglonCorte.dividirAbonoCapital(e, t, c, false);
        ParteCorte deCapital = partes.get(0);
        ParteCorte deRefrendo = partes.get(1);

        assertThat(deCapital.total()).as("capital").isEqualByComparingTo(c);
        assertThat(deRefrendo.total()).as("refrendo").isEqualByComparingTo(e.add(t).subtract(c));
        assertThat(deCapital.efectivo().add(deRefrendo.efectivo())).as("efectivo").isEqualByComparingTo(e);
        assertThat(deCapital.tarjeta().add(deRefrendo.tarjeta())).as("tarjeta").isEqualByComparingTo(t);
        for (ParteCorte p : partes) {
            assertThat(p.efectivo().signum()).as("efectivo >= 0 en " + p).isGreaterThanOrEqualTo(0);
            assertThat(p.tarjeta().signum()).as("tarjeta >= 0 en " + p).isGreaterThanOrEqualTo(0);
            assertThat(p.efectivo().scale()).isLessThanOrEqualTo(2);
            assertThat(p.tarjeta().scale()).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void divisionRc_abonoFueraDeRango_falla() {
        assertThatThrownBy(() -> ResolutorRenglonCorte.dividirAbonoCapital(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.01"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResolutorRenglonCorte.dividirAbonoCapital(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("-1.00"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Q-C1: las cancelaciones restan del renglón de la operación original
    // =========================================================================

    @Test
    void refrendoCanceladoElMismoDia_dejaRefrendosEnCero() {
        MovimientoContrato rf = contrato(TipoMovimiento.RF, "0.00", "0.00");
        MovimientoCaja cobro = flujo(ConceptoCaja.REFRENDO, "210.00", "100.00", rf);
        MovimientoCaja devolucion = flujo(ConceptoCaja.DEVOLUCION_COBRO, "210.00", "100.00", rf);

        Map<RenglonCorte, ParteCorte> totales = resolutor.totalesPorRenglon(List.of(cobro, devolucion));

        assertParte(totales.get(RenglonCorte.REFRENDOS), "0", "0");
        assertTodoEnCero(totales);
    }

    @Test
    void rcCanceladoElMismoDia_dejaCapitalYRefrendosEnCero() {
        MovimientoContrato rc = contrato(TipoMovimiento.RC, "500.00", "0.00");
        MovimientoCaja cobro = flujo(ConceptoCaja.ABONO_CAPITAL, "333.33", "666.67", rc);
        MovimientoCaja devolucion = flujo(ConceptoCaja.DEVOLUCION_COBRO, "333.33", "666.67", rc);

        assertTodoEnCero(resolutor.totalesPorRenglon(List.of(cobro, devolucion)));
    }

    @Test
    void empenoCanceladoElMismoDia_dejaEmpenosEnCero() {
        MovimientoContrato emp = contrato(TipoMovimiento.EMP, "0.00", "0.00");
        MovimientoCaja prestamo = flujo(ConceptoCaja.EMPENO, "3000.00", "0.00", emp);
        MovimientoCaja devuelto = flujo(ConceptoCaja.CANCELACION_EMPENO, "3000.00", "0.00", emp);

        assertTodoEnCero(resolutor.totalesPorRenglon(List.of(prestamo, devuelto)));
    }

    @Test
    void cancelacionDeUnCobroDeOtroDia_restaEnElRenglonDelCobroElDiaQueSeCancela() {
        // Solo la fila de la cancelación es del día: el RX se cobró ayer
        MovimientoContrato rx = contrato(TipoMovimiento.RX, "0.00", "30.00");
        MovimientoCaja devolucion = flujo(ConceptoCaja.DEVOLUCION_COBRO, "250.00", "0.00", rx);

        Map<RenglonCorte, ParteCorte> totales = resolutor.totalesPorRenglon(List.of(devolucion));

        assertParte(totales.get(RenglonCorte.REFRENDOS_EXTEMPORANEOS), "-250.00", "0.00");
        assertParte(totales.get(RenglonCorte.DEVOLUCIONES), "0", "0");
    }

    @Test
    void ventaCanceladaAlDiaSiguiente_restaEnVentasDeEseDia() {
        MovimientoCaja venta = flujo(ConceptoCaja.VENTA_VITRINA, "1000.00", "500.00", OrigenCaja.VENTA, 7L);
        MovimientoCaja cancelacion = flujo(ConceptoCaja.CANCELACION_VENTA, "1000.00", "500.00",
                OrigenCaja.CANCELACION_VENTA, 3L);

        Map<RenglonCorte, ParteCorte> diaDeLaVenta = resolutor.totalesPorRenglon(List.of(venta));
        Map<RenglonCorte, ParteCorte> diaSiguiente = resolutor.totalesPorRenglon(List.of(cancelacion));

        assertParte(diaDeLaVenta.get(RenglonCorte.VENTAS), "1000.00", "500.00");
        assertParte(diaSiguiente.get(RenglonCorte.VENTAS), "-1000.00", "-500.00");
        // La venta no se lee: vitrina y fundición van las dos a VENTAS (A-02)
        verifyNoInteractions(movimientoContratoRepository);
    }

    @Test
    void ventaDeFundicionCanceladaElMismoDia_dejaVentasEnCero() {
        MovimientoCaja venta = flujo(ConceptoCaja.VENTA_FUNDICION, "4200.00", "0.00", OrigenCaja.VENTA, 8L);
        MovimientoCaja cancelacion = flujo(ConceptoCaja.CANCELACION_VENTA, "4200.00", "0.00",
                OrigenCaja.CANCELACION_VENTA, 4L);

        assertTodoEnCero(resolutor.totalesPorRenglon(List.of(venta, cancelacion)));
    }

    @Test
    void cancelacionSinOrigen_falla() {
        MovimientoCaja huerfana = flujo(ConceptoCaja.DEVOLUCION_COBRO, "100.00", "0.00", null, null);

        assertThatThrownBy(() -> resolutor.partes(List.of(huerfana)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene origen");
    }

    @Test
    void rcSinSuMovimientoDeContrato_falla() {
        MovimientoCaja rc = flujo(ConceptoCaja.ABONO_CAPITAL, "100.00", "0.00", OrigenCaja.MOVIMIENTO_CONTRATO, 999L);

        assertThatThrownBy(() -> resolutor.partes(List.of(rc)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movimiento de contrato");
    }

    @Test
    void losMovimientosDeContratoSeLeenEnUnSoloSelect() {
        MovimientoContrato rc1 = contrato(TipoMovimiento.RC, "100.00", "0.00");
        MovimientoContrato rc2 = contrato(TipoMovimiento.RC, "200.00", "0.00");
        MovimientoContrato fi = contrato(TipoMovimiento.FI, "0.00", "0.00");

        resolutor.partes(List.of(
                flujo(ConceptoCaja.ABONO_CAPITAL, "150.00", "0.00", rc1),
                flujo(ConceptoCaja.ABONO_CAPITAL, "260.00", "0.00", rc2),
                flujo(ConceptoCaja.FINIQUITO, "900.00", "0.00", fi),
                flujo(ConceptoCaja.DEVOLUCION_COBRO, "150.00", "0.00", rc1)));

        verify(movimientoContratoRepository, times(1)).findAllById(any());
    }

    // =========================================================================
    // Cobertura: ningún tipo de movimiento queda fuera del corte
    // =========================================================================

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void todoMovimientoQueMueveCaja_caeEnRenglones_ySuCancelacionLoDejaEnCero(TipoMovimiento tipo) {
        if (ConceptoCaja.TIPOS_SIN_CAJA.contains(tipo)) {
            return;
        }
        MovimientoContrato mov = contrato(tipo, tipo == TipoMovimiento.RC ? "400.00" : "0.00", "0.00");
        MovimientoCaja cobro = flujo(ConceptoCaja.deMovimiento(tipo), "457.77", "123.45", mov);

        List<ParteCorte> partes = resolutor.partes(List.of(cobro));
        assertThat(partes).as("partes de " + tipo).isNotEmpty();
        assertThat(partes.stream().map(ParteCorte::total).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("lo que suma " + tipo).isEqualByComparingTo("581.22");
        assertThat(partes).extracting(ParteCorte::renglon)
                .doesNotContain(RenglonCorte.ABONOS_PARCIALES, RenglonCorte.DEVOLUCIONES);

        if (tipo == TipoMovimiento.RE || tipo == TipoMovimiento.RM) {
            return;  // RE no se cancela (C-03) y RM todavía no tiene cancelación
        }
        MovimientoCaja cancelacion = flujo(ConceptoCaja.deCancelacion(tipo), "457.77", "123.45", mov);
        assertThat(resolutor.partes(List.of(cancelacion)))
                .as("la cancelación de " + tipo + " resta lo mismo de los mismos renglones")
                .isEqualTo(partes.stream().map(ParteCorte::negada).toList());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private MovimientoContrato contrato(TipoMovimiento tipo, String abonoCapital, String sancion) {
        MovimientoContrato m = new MovimientoContrato();
        m.setId(siguienteId++);
        m.setTipo(tipo);
        m.setAbonoCapital(new BigDecimal(abonoCapital));
        m.setSancion(new BigDecimal(sancion));
        contratos.add(m);
        return m;
    }

    private MovimientoCaja flujo(ConceptoCaja concepto, String efectivo, String tarjeta, MovimientoContrato origen) {
        return flujo(concepto, efectivo, tarjeta, OrigenCaja.MOVIMIENTO_CONTRATO, origen.getId());
    }

    private MovimientoCaja flujo(ConceptoCaja concepto, String efectivo, String tarjeta,
                                 OrigenCaja origenTipo, Long origenId) {
        MovimientoCaja m = new MovimientoCaja();
        m.setId(siguienteId++);
        m.setConceptoCaja(concepto);
        m.setTipo(concepto.tipo());
        m.setImporteEfectivo(new BigDecimal(efectivo));
        m.setImporteTarjeta(new BigDecimal(tarjeta));
        m.setMonto(m.getImporteEfectivo().add(m.getImporteTarjeta()));
        m.setOrigenTipo(origenTipo);
        m.setOrigenId(origenId);
        return m;
    }

    private static void assertParte(ParteCorte parte, String efectivo, String tarjeta) {
        assertThat(parte.efectivo()).as("efectivo de " + parte.renglon()).isEqualByComparingTo(efectivo);
        assertThat(parte.tarjeta()).as("tarjeta de " + parte.renglon()).isEqualByComparingTo(tarjeta);
    }

    private static void assertTodoEnCero(Map<RenglonCorte, ParteCorte> totales) {
        assertThat(totales).hasSize(RenglonCorte.values().length);
        totales.values().forEach(p -> assertParte(p, "0", "0"));
    }
}
