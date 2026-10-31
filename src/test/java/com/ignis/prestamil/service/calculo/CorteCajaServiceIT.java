package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
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
import java.time.LocalDateTime;
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
@Import({CorteCajaService.class, ResolutorRenglonCorte.class, SaldoArranqueConfiguracionProvider.class})
class CorteCajaServiceIT {

    private static final int SUCURSAL = 1;
    private static final LocalDate DIA_5_4 = LocalDate.of(2026, 9, 17);
    /** Tolerancia de 5.4 para las cifras que COCAE calcula con más decimales. */
    private static final BigDecimal COCAE = new BigDecimal("0.02");

    @Autowired CorteCajaService corteCajaService;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired EntityManager em;

    private Caja concentradora;
    private Caja boveda;
    private Caja ventanilla;
    private Usuario cajero;
    private Turno turno;
    private Contrato contrato;
    private long folio = 1;

    @BeforeEach
    void sembrar() {
        concentradora = caja(TipoCaja.CONCENTRADORA, "001", "CUENTA CONCENTRADORA");
        boveda = caja(TipoCaja.BOVEDA, "002", "BÓVEDA");
        ventanilla = caja(TipoCaja.VENTANILLA, "003", "VENTANILLA");

        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (3, 'Cajero', TRUE)").executeUpdate();
        cajero = new Usuario();
        cajero.setNombreUsuario("cajero");
        cajero.setPassword("x");
        cajero.setNombre("cajero");
        cajero.setApellidos("Prueba");
        cajero.setEstatus(true);
        cajero.setCreado(DIA_5_4.atStartOfDay());
        cajero.setRol(em.find(Rol.class, 3));
        cajero.setVigencia(true);
        cajero.setFechaCambioPass(DIA_5_4);
        em.persist(cajero);

        turno = new Turno();
        turno.setUsuario(cajero);
        turno.setFechaInicio(DIA_5_4.atTime(8, 0));
        turno.setActivo(true);
        em.persist(turno);

        contrato = contrato();
    }

    @Test
    void corteDel17deSeptiembre_reproduceLaSeccion5_4() {
        arranque(DIA_5_4, "41013.11");
        traspaso(TipoTraspasoCaja.CONCENTRACION, "1000.00", DIA_5_4);
        traspaso(TipoTraspasoCaja.DOTACION, "18861.85", DIA_5_4);
        traspaso(TipoTraspasoCaja.RECOLECCION, "43889.50", DIA_5_4);
        // Un traspaso cancelado no cuenta
        traspaso(TipoTraspasoCaja.DOTACION, "500.00", DIA_5_4).setEstatus(EstatusTraspasoCaja.CANCELADO);

        flujo(ConceptoCaja.EMPENO, "30000.00", "0.00", null, null, 9);
        flujo(ConceptoCaja.EMPENO, "2370.00", "0.00", null, null, 10);
        // RC: 10,489 de capital van a ABONOS A CAPITAL y 1,012 de interés e IVA a REFRENDOS (Q-C3)
        MovimientoContrato rc = movimientoContrato(TipoMovimiento.RC, "11501.00", "10489.00", "0.00");
        flujo(ConceptoCaja.ABONO_CAPITAL, "11501.00", "0.00", OrigenCaja.MOVIMIENTO_CONTRATO, rc.getId(), 11);
        flujo(ConceptoCaja.FINIQUITO, "20000.00", "0.00", null, null, 12);
        flujo(ConceptoCaja.FINIQUITO, "5017.29", "0.00", null, null, 12);
        flujo(ConceptoCaja.REFRENDO, "9000.00", "0.00", null, null, 13);
        flujo(ConceptoCaja.REFRENDO, "5375.26", "0.00", null, null, 13);
        flujo(ConceptoCaja.REFRENDO_EXTEMPORANEO, "4153.24", "0.00", null, null, 14);
        // Nota 9182 (5.2)
        flujo(ConceptoCaja.VENTA_VITRINA, "2350.87", "0.00", OrigenCaja.VENTA, 9182L, 15);
        // Un refrendo cobrado y cancelado el mismo día queda en neto 0 y no tiene renglón (Q-C1)
        MovimientoContrato rf = movimientoContrato(TipoMovimiento.RF, "700.00", "0.00", "0.00");
        flujo(ConceptoCaja.REFRENDO, "700.00", "0.00", OrigenCaja.MOVIMIENTO_CONTRATO, rf.getId(), 16);
        flujo(ConceptoCaja.DEVOLUCION_COBRO, "700.00", "0.00", OrigenCaja.MOVIMIENTO_CONTRATO, rf.getId(), 17);
        em.flush();

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
        arranque(dia, "10000.00");
        traspaso(TipoTraspasoCaja.DOTACION, "1000.00", dia);
        // Refrendo de 1,000: 600 en efectivo y 400 con tarjeta (RC-06)
        flujo(ConceptoCaja.REFRENDO, "600.00", "400.00", null, null, dia.atTime(11, 0));
        traspaso(TipoTraspasoCaja.RECOLECCION, "1600.00", dia);
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
        arranque(dia.minusDays(2), "5000.00");
        traspaso(TipoTraspasoCaja.MINISTRACION, "1000.00", dia.minusDays(1));
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
        arranque(d1, "1000.00");
        // Día 1: venta en efectivo, todo se recolecta
        traspaso(TipoTraspasoCaja.MINISTRACION, "5000.00", d1);
        traspaso(TipoTraspasoCaja.DOTACION, "2000.00", d1);
        flujo(ConceptoCaja.VENTA_VITRINA, "500.00", "0.00", OrigenCaja.VENTA, 7L, d1.atTime(12, 0));
        traspaso(TipoTraspasoCaja.RECOLECCION, "2500.00", d1);
        // Día 2: se cancela la venta de ayer; resta en VENTAS de hoy (Q-C1) y la gaveta queda con 100 de más
        traspaso(TipoTraspasoCaja.DOTACION, "1000.00", d2);
        flujo(ConceptoCaja.CANCELACION_VENTA, "500.00", "0.00", OrigenCaja.CANCELACION_VENTA, 3L, d2.atTime(9, 0));
        traspaso(TipoTraspasoCaja.RECOLECCION, "400.00", d2);
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
        arranque(dia, "5000.00");
        traspaso(TipoTraspasoCaja.DOTACION, "1000.00", dia);
        traspaso(TipoTraspasoCaja.RECOLECCION, "1000.03", dia);
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

    private void arranque(LocalDate fecha, String saldo) {
        configuracion(SaldoArranqueConfiguracionProvider.CAJA_FECHA_ARRANQUE, fecha.toString());
        configuracion(SaldoArranqueConfiguracionProvider.CAJA_SALDO_ARRANQUE, saldo);
    }

    private void configuracion(String clave, String valor) {
        Configuracion c = new Configuracion();
        c.setConfiguracion(clave);
        c.setValorCadena(valor);
        configuracionRepository.save(c);
    }

    private Caja caja(TipoCaja tipo, String clave, String nombre) {
        Caja c = new Caja();
        c.setSucursalId(SUCURSAL);
        c.setTipo(tipo);
        c.setClave(clave);
        c.setNombre(nombre);
        em.persist(c);
        return c;
    }

    /** Traspaso activo; el tipo fija las cajas (RC-02). */
    private TraspasoCaja traspaso(TipoTraspasoCaja tipo, String importe, LocalDate fecha) {
        TraspasoCaja t = new TraspasoCaja();
        t.setSucursalId(SUCURSAL);
        t.setFolio(folio++);
        t.setTipo(tipo);
        switch (tipo) {
            case MINISTRACION -> { t.setCajaOrigen(concentradora); t.setCajaDestino(boveda); }
            case CONCENTRACION -> { t.setCajaOrigen(boveda); t.setCajaDestino(concentradora); }
            case DOTACION -> { t.setCajaOrigen(boveda); t.setCajaDestino(ventanilla); }
            case RECOLECCION -> { t.setCajaOrigen(ventanilla); t.setCajaDestino(boveda); }
        }
        t.setImporte(new BigDecimal(importe));
        t.setFechaOperacion(fecha);
        t.setFechaRegistro(fecha.atTime(9, 0));
        t.setUsuario(cajero);
        em.persist(t);
        return t;
    }

    private void flujo(ConceptoCaja concepto, String efectivo, String tarjeta, OrigenCaja origenTipo,
                       Long origenId, int hora) {
        flujo(concepto, efectivo, tarjeta, origenTipo, origenId, DIA_5_4.atTime(hora, 0));
    }

    private void flujo(ConceptoCaja concepto, String efectivo, String tarjeta, OrigenCaja origenTipo,
                       Long origenId, LocalDateTime fecha) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurno(turno);
        m.setSucursalId(SUCURSAL);
        m.setTipo(concepto.tipo());
        m.setConceptoCaja(concepto);
        m.setConcepto(concepto.name());
        m.setImporteEfectivo(new BigDecimal(efectivo));
        m.setImporteTarjeta(new BigDecimal(tarjeta));
        m.setMonto(m.getImporteEfectivo().add(m.getImporteTarjeta()));
        m.setOrigenTipo(origenTipo);
        m.setOrigenId(origenId);
        m.setUsuario(cajero);
        m.setFecha(fecha);
        em.persist(m);
    }

    private MovimientoContrato movimientoContrato(TipoMovimiento tipo, String monto, String abonoCapital,
                                                  String sancion) {
        MovimientoContrato mc = new MovimientoContrato();
        mc.setContrato(contrato);
        mc.setTurno(turno);
        mc.setUsuario(cajero);
        mc.setTipo(tipo);
        mc.setMonto(new BigDecimal(monto));
        mc.setAbonoCapital(new BigDecimal(abonoCapital));
        mc.setSancion(new BigDecimal(sancion));
        mc.setFecha(DIA_5_4.atTime(11, 0));
        em.persist(mc);
        return mc;
    }

    private Contrato contrato() {
        Plazo plazo = new Plazo();
        plazo.setNombre("Semanal");
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        em.persist(plazo);

        Direccion d = new Direccion();
        d.setTipoDireccion(Direccion.TipoDireccion.Particular);
        d.setCalle("Calle");
        d.setNumeroExterior("1");
        d.setColonia("Centro");
        d.setCiudad("Pinotepa Nacional");
        d.setEstado("Oaxaca");
        d.setCodigoPostal("71600");
        d.setFechaRegistro(DIA_5_4);
        d.setEsVerificada(false);
        em.persist(d);

        Cliente cliente = new Cliente();
        cliente.setNombre("Juan");
        cliente.setApellidoPaterno("Pérez");
        cliente.setApellidoMaterno("López");
        cliente.setTelefono("9540000001");
        cliente.setActivo(true);
        cliente.setDireccion(d);
        em.persist(cliente);

        Contrato c = new Contrato();
        c.setFolio("CTR-5-4");
        c.setCliente(cliente);
        c.setTurno(turno);
        c.setUsuario(cajero);
        c.setPlazo(plazo);
        c.setSucursalId(SUCURSAL);
        c.setFechaApertura(DIA_5_4.minusDays(28).atStartOfDay());
        c.setFechaContrato(DIA_5_4.minusDays(28));
        c.setFechaVencimiento(DIA_5_4);
        c.setFechaComercializacion(DIA_5_4.plusDays(15));
        c.setMontoPrestamo(new BigDecimal("20000.00"));
        c.setSaldoCapital(new BigDecimal("20000.00"));
        c.setMontoAvaluo(new BigDecimal("26000.00"));
        c.setEstatus(EstatusContrato.VIGENTE);
        em.persist(c);
        return c;
    }
}
