package com.ignis.prestamil.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regla de mapeo del corte de caja (CAJ-01b, ajustada en CAJ-01d con las respuestas de Jorge del
 * 2026-10-30; sección 3 de PLAN-CORTE-CAJA): todo movimiento de contrato que mueve caja tiene concepto, y
 * todo concepto tiene renglón o es una cancelación que resta del renglón de la operación original. Si
 * alguien agrega un TipoMovimiento o un ConceptoCaja sin decidir a dónde va en el corte, esta prueba falla.
 * La resolución completa (RC dividido, cancelaciones) se prueba en {@code ResolutorRenglonCorteTest}.
 */
class ConceptoCajaTest {

    private static final Set<ConceptoCaja> CANCELACIONES = EnumSet.of(
            ConceptoCaja.CANCELACION_EMPENO, ConceptoCaja.DEVOLUCION_COBRO, ConceptoCaja.CANCELACION_VENTA);

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void todoTipoDeMovimiento_tieneConceptoDeCaja_oEstaMarcadoComoSinCaja(TipoMovimiento tipo) {
        if (ConceptoCaja.TIPOS_SIN_CAJA.contains(tipo)) {
            assertThatThrownBy(() -> ConceptoCaja.deMovimiento(tipo))
                    .isInstanceOf(IllegalArgumentException.class);
            return;
        }
        ConceptoCaja concepto = ConceptoCaja.deMovimiento(tipo);
        assertThat(concepto).as("concepto de caja de " + tipo).isNotNull();
        assertThat(concepto.esCancelacion()).as("un cobro no es cancelación: " + tipo).isFalse();
        assertThat(concepto.renglonCorte()).as("renglón del corte de " + tipo).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(ConceptoCaja.class)
    void todoConcepto_tieneRenglonConSuDireccion_oEsCancelacion(ConceptoCaja concepto) {
        assertThat(concepto.tipo()).isNotNull();
        assertThat(concepto.esCancelacion()).as(concepto.name()).isEqualTo(CANCELACIONES.contains(concepto));
        if (concepto.esCancelacion()) {
            // Q-C1: sin renglón propio
            assertThatThrownBy(concepto::renglonCorte).isInstanceOf(IllegalStateException.class);
            return;
        }
        RenglonCorte.Columna esperada = concepto.tipo() == TipoMovimientoCaja.ENTRADA
                ? RenglonCorte.Columna.INGRESO
                : RenglonCorte.Columna.EGRESO;
        assertThat(concepto.renglonCorte().getColumna()).as(concepto.name()).isEqualTo(esperada);
    }

    @Test
    void mapeoDeCobros_segunLaTablaDelCorte() {
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.EMP)).isEqualTo(ConceptoCaja.EMPENO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RF)).isEqualTo(ConceptoCaja.REFRENDO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RPG)).isEqualTo(ConceptoCaja.REFRENDO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RX)).isEqualTo(ConceptoCaja.REFRENDO_EXTEMPORANEO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RC)).isEqualTo(ConceptoCaja.ABONO_CAPITAL);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.FI)).isEqualTo(ConceptoCaja.FINIQUITO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.FX)).isEqualTo(ConceptoCaja.FINIQUITO_EXTEMPORANEO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RE)).isEqualTo(ConceptoCaja.REPOSICION);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RM)).isEqualTo(ConceptoCaja.REMANENTE);
    }

    @Test
    void refrendosParciales_sonRefrendos_Q_C3() {
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RP)).isEqualTo(ConceptoCaja.REFRENDO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RP).renglonCorte()).isEqualTo(RenglonCorte.REFRENDOS);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RPX)).isEqualTo(ConceptoCaja.REFRENDO_EXTEMPORANEO);
        assertThat(ConceptoCaja.deMovimiento(TipoMovimiento.RPX).renglonCorte())
                .isEqualTo(RenglonCorte.REFRENDOS_EXTEMPORANEOS);
    }

    @Test
    void abonosParcialesYDevoluciones_noRecibenNingunConcepto() {
        Set<RenglonCorte> conConcepto = Arrays.stream(ConceptoCaja.values())
                .filter(c -> !c.esCancelacion())
                .map(ConceptoCaja::renglonCorte)
                .collect(Collectors.toSet());

        assertThat(conConcepto).doesNotContain(RenglonCorte.ABONOS_PARCIALES, RenglonCorte.DEVOLUCIONES);
    }

    @Test
    void elCorteYaNoTieneRenglonesDeCancelacion_Q_C1() {
        assertThat(Arrays.stream(RenglonCorte.values()).map(Enum::name))
                .doesNotContain("CANCELACION_EMPENOS", "CANCELACIONES_VENTA")
                .contains("ABONOS_PARCIALES", "DEVOLUCIONES");
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimiento.class, names = {"RF", "RPG", "RX", "RP", "RPX", "RC", "FI", "FX"})
    void cancelarUnCobro_esDevolucionDeCobro_ySaleDeCaja(TipoMovimiento tipo) {
        ConceptoCaja concepto = ConceptoCaja.deCancelacion(tipo);
        assertThat(concepto).isEqualTo(ConceptoCaja.DEVOLUCION_COBRO);
        assertThat(concepto.esCancelacion()).isTrue();
        // La cancelación va en la dirección contraria al cobro
        assertThat(concepto.tipo()).isNotEqualTo(ConceptoCaja.deMovimiento(tipo).tipo());
    }

    @Test
    void cancelarElEmpeno_esCancelacionDeEmpeno_yEntraACaja() {
        assertThat(ConceptoCaja.deCancelacion(TipoMovimiento.EMP)).isEqualTo(ConceptoCaja.CANCELACION_EMPENO);
        assertThat(ConceptoCaja.CANCELACION_EMPENO.tipo()).isEqualTo(TipoMovimientoCaja.ENTRADA);
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimiento.class, names = {"RE", "RM", "PV", "PVA"})
    void cancelacionesSinFlujoDeCaja_noTienenConcepto(TipoMovimiento tipo) {
        assertThatThrownBy(() -> ConceptoCaja.deCancelacion(tipo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
