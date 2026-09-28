package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.TipoOperacion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.ignis.prestamil.model.AccionContrato.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estatus operativo derivado y matriz de acciones disponibles (RN-16, RN-17).
 */
class EstatusContratoResolverTest {

    private static final int GRACIA = 2;
    private static final LocalDate VENCIMIENTO = LocalDate.of(2026, 9, 29);
    private static final LocalDate COMERCIALIZACION = VENCIMIENTO.plusDays(15);

    private static Contrato contrato(EstatusContrato estatus, EstatusPartida... partidas) {
        Contrato c = new Contrato();
        c.setEstatus(estatus);
        c.setFechaContrato(VENCIMIENTO.minusDays(28));
        c.setFechaVencimiento(VENCIMIENTO);
        c.setFechaComercializacion(COMERCIALIZACION);
        List<PartidaContrato> ps = new ArrayList<>();
        for (EstatusPartida e : partidas) {
            PartidaContrato p = new PartidaContrato();
            p.setEstatus(e);
            ps.add(p);
        }
        c.setPartidas(ps);
        return c;
    }

    // =========================================================================
    // Estatus derivado de las fechas
    // =========================================================================

    @Nested
    class EstatusPorFechas {

        private EstatusOperativo estatusEl(LocalDate hoy) {
            return EstatusContratoResolver.estatusDerivado(
                    contrato(EstatusContrato.VIGENTE, EstatusPartida.OP), GRACIA, hoy);
        }

        @Test
        void elDiaDelVencimiento_esVigente() {
            assertThat(estatusEl(VENCIMIENTO)).isEqualTo(EstatusOperativo.VIGENTE);
        }

        @Test
        void unoYDosDiasDespues_esEnGracia() {
            assertThat(estatusEl(VENCIMIENTO.plusDays(1))).isEqualTo(EstatusOperativo.EN_GRACIA);
            assertThat(estatusEl(VENCIMIENTO.plusDays(2))).isEqualTo(EstatusOperativo.EN_GRACIA);
        }

        @Test
        void rebasadaLaGraciaYAntesDeComercializar_esVencido() {
            assertThat(estatusEl(VENCIMIENTO.plusDays(3))).isEqualTo(EstatusOperativo.VENCIDO);
            assertThat(estatusEl(COMERCIALIZACION.minusDays(1))).isEqualTo(EstatusOperativo.VENCIDO);
        }

        @Test
        void desdeLaFechaDeComercializacion_esEnVenta() {
            assertThat(estatusEl(COMERCIALIZACION)).isEqualTo(EstatusOperativo.EN_VENTA);
        }

        @Test
        void sinFechaDeComercializacion_usaVencimientoMas15() {
            Contrato c = contrato(EstatusContrato.VIGENTE, EstatusPartida.OP);
            c.setFechaComercializacion(null);

            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, VENCIMIENTO.plusDays(14)))
                    .isEqualTo(EstatusOperativo.VENCIDO);
            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, VENCIMIENTO.plusDays(15)))
                    .isEqualTo(EstatusOperativo.EN_VENTA);
        }

        @Test
        void graciaCero_elDiaSiguienteYaEsVencido() {
            Contrato c = contrato(EstatusContrato.VIGENTE, EstatusPartida.OP);
            assertThat(EstatusContratoResolver.estatusDerivado(c, 0, VENCIMIENTO.plusDays(1)))
                    .isEqualTo(EstatusOperativo.VENCIDO);
        }

        @Test
        void vencidoPersistidoPeroYaRefrendado_mandanLasFechas() {
            // El estatus persistido puede ir atrasado respecto a las fechas (el pase diario no ha corrido).
            Contrato c = contrato(EstatusContrato.VENCIDO, EstatusPartida.OP);
            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, VENCIMIENTO.minusDays(3)))
                    .isEqualTo(EstatusOperativo.VIGENTE);
        }
    }

    // =========================================================================
    // Estatus persistidos y de partidas
    // =========================================================================

    @Nested
    class EstatusPersistidoYPartidas {

        private final LocalDate vigenteHoy = VENCIMIENTO.minusDays(5);

        @Test
        void finiquitadoCanceladoYVendido_mandanSobreLasFechas() {
            assertThat(EstatusContratoResolver.estatusDerivado(
                    contrato(EstatusContrato.FINIQUITADO, EstatusPartida.FIN), GRACIA, vigenteHoy))
                    .isEqualTo(EstatusOperativo.FINIQUITADO);
            assertThat(EstatusContratoResolver.estatusDerivado(
                    contrato(EstatusContrato.CANCELADO, EstatusPartida.OP), GRACIA, vigenteHoy))
                    .isEqualTo(EstatusOperativo.CANCELADO);
            assertThat(EstatusContratoResolver.estatusDerivado(
                    contrato(EstatusContrato.VENDIDO, EstatusPartida.VEN), GRACIA, vigenteHoy))
                    .isEqualTo(EstatusOperativo.VENDIDO);
        }

        @Test
        void unaSolaPartidaApartada_bloqueaTodoElContrato() {
            Contrato c = contrato(EstatusContrato.EN_VENTA, EstatusPartida.OP, EstatusPartida.APA);
            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, COMERCIALIZACION.plusDays(3)))
                    .isEqualTo(EstatusOperativo.APARTADO);
        }

        @Test
        void unaSolaPartidaVendida_esVendido() {
            Contrato c = contrato(EstatusContrato.EN_VENTA, EstatusPartida.VEN, EstatusPartida.OP);
            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, COMERCIALIZACION.plusDays(3)))
                    .isEqualTo(EstatusOperativo.VENDIDO);
        }

        @Test
        void enVentaPersistido_seRespetaAunqueLasFechasDiganVencido() {
            // Pase a venta anticipado (F12) o pase diario: el estatus persistido EN_VENTA se respeta.
            Contrato c = contrato(EstatusContrato.EN_VENTA, EstatusPartida.OP);
            assertThat(EstatusContratoResolver.estatusDerivado(c, GRACIA, VENCIMIENTO.plusDays(5)))
                    .isEqualTo(EstatusOperativo.EN_VENTA);
        }
    }

    // =========================================================================
    // Matriz RN-16
    // =========================================================================

    static Stream<Arguments> matriz() {
        Set<AccionContrato> vigenteOGracia = EnumSet.of(REFRENDO, FINIQUITO, ABONO_CAPITAL, REFRENDO_PARCIAL,
                REPOSICION, CONSULTA, CANCELACION);
        Set<AccionContrato> vencidoOEnVenta = EnumSet.of(REFRENDO_PARCIAL, REFRENDO_EXTEMPORANEO,
                FINIQUITO_EXTEMPORANEO, REPOSICION, CONSULTA, CANCELACION);
        Set<AccionContrato> cerrado = EnumSet.of(CONSULTA, CANCELACION);
        return Stream.of(
                Arguments.of(EstatusOperativo.VIGENTE, vigenteOGracia),
                Arguments.of(EstatusOperativo.EN_GRACIA, vigenteOGracia),
                Arguments.of(EstatusOperativo.VENCIDO, vencidoOEnVenta),
                Arguments.of(EstatusOperativo.EN_VENTA, vencidoOEnVenta),
                Arguments.of(EstatusOperativo.APARTADO, cerrado),
                Arguments.of(EstatusOperativo.VENDIDO, cerrado),
                Arguments.of(EstatusOperativo.FINIQUITADO, cerrado),
                Arguments.of(EstatusOperativo.CANCELADO, cerrado));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void accionesDisponibles_segunMatrizRn16(EstatusOperativo estatus, Set<AccionContrato> esperadas) {
        assertThat(EstatusContratoResolver.accionesDisponibles(estatus, 5))
                .containsExactlyInAnyOrderElementsOf(esperadas);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = EstatusOperativo.class, names = {"VIGENTE", "EN_GRACIA", "VENCIDO", "EN_VENTA"})
    void refrendoParcial_requiereAlMenosDosPeriodosTranscurridos(EstatusOperativo estatus) {
        assertThat(EstatusContratoResolver.accionesDisponibles(estatus, 1)).doesNotContain(REFRENDO_PARCIAL);
        assertThat(EstatusContratoResolver.accionesDisponibles(estatus, 2)).contains(REFRENDO_PARCIAL);
    }

    // =========================================================================
    // Operacion pedida → boton de la matriz
    // =========================================================================

    @Test
    void refrendoYFiniquito_enVencidoOEnVenta_sonLosExtemporaneos() {
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.REFRENDO, EstatusOperativo.VENCIDO))
                .isEqualTo(REFRENDO_EXTEMPORANEO);
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.FINIQUITO, EstatusOperativo.EN_VENTA))
                .isEqualTo(FINIQUITO_EXTEMPORANEO);
    }

    @Test
    void refrendoYFiniquito_enVigenteOGracia_sonLosNormales() {
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.REFRENDO, EstatusOperativo.EN_GRACIA))
                .isEqualTo(REFRENDO);
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.FINIQUITO, EstatusOperativo.VIGENTE))
                .isEqualTo(FINIQUITO);
    }

    @Test
    void abonoYParcial_siempreSonSuPropioBoton() {
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.ABONO_CAPITAL, EstatusOperativo.VENCIDO))
                .isEqualTo(ABONO_CAPITAL);
        assertThat(EstatusContratoResolver.accionPara(TipoOperacion.REFRENDO_PARCIAL, EstatusOperativo.VIGENTE))
                .isEqualTo(REFRENDO_PARCIAL);
    }
}
