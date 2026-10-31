package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.CajaArranque;
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
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos de prueba del corte de caja contra H2: cajas, cajero, turno y contrato, más ayudas para sembrar
 * saldo de arranque, traspasos y flujos de ventanilla. {@link #sembrar17deSeptiembre()} siembra el corte del
 * 17/09/2026 de Pinotepa 2 (sección 5.4 de PLAN-VENTAS-CAJA-REPORTES); lo usan el motor (CAJ-05) y su
 * reporte (REP-04).
 */
public class CorteCajaSemilla {

    public static final int SUCURSAL = 1;
    public static final LocalDate DIA_5_4 = LocalDate.of(2026, 9, 17);

    private final EntityManager em;
    private final ConfiguracionRepository configuracionRepository;

    private final Caja concentradora;
    private final Caja boveda;
    private final Caja ventanilla;
    private final Usuario cajero;
    private final Turno turno;
    private final Contrato contrato;
    private long folio = 1;

    /** Siembra cajas, el cajero (rol 3), su turno y un contrato. */
    public CorteCajaSemilla(EntityManager em, ConfiguracionRepository configuracionRepository) {
        this.em = em;
        this.configuracionRepository = configuracionRepository;

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

    /**
     * Movimientos del 17/09/2026 de Pinotepa 2 (5.4): arranque de 41,013.11, concentración de 1,000,
     * dotación de 18,861.85, recolección de 43,889.50 y los flujos de ventanilla de cada renglón. Además
     * una dotación cancelada y un refrendo cobrado y cancelado el mismo día, que no deben verse.
     */
    public void sembrar17deSeptiembre() {
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
    }

    /** Fecha y saldo de arranque de caja ({@code caja_arranque}), ya capturados por Sistemas (CAJ-06). */
    public CajaArranque arranque(LocalDate fecha, String saldo) {
        CajaArranque a = new CajaArranque();
        a.setSucursalId(SUCURSAL);
        a.setFechaArranque(fecha);
        a.setSaldoBoveda(new BigDecimal(saldo));
        a.setConfirmado(true);
        em.persist(a);
        return a;
    }

    /** Traspaso activo; el tipo fija las cajas (RC-02). */
    public TraspasoCaja traspaso(TipoTraspasoCaja tipo, String importe, LocalDate fecha) {
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

    /** Flujo de ventanilla del 17/09 a la hora indicada. */
    public void flujo(ConceptoCaja concepto, String efectivo, String tarjeta, OrigenCaja origenTipo,
                      Long origenId, int hora) {
        flujo(concepto, efectivo, tarjeta, origenTipo, origenId, DIA_5_4.atTime(hora, 0));
    }

    public void flujo(ConceptoCaja concepto, String efectivo, String tarjeta, OrigenCaja origenTipo,
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

    /** Renglón de {@code configuraciones}, p. ej. los roles de un permiso. */
    public void configuracion(String clave, String valor) {
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
