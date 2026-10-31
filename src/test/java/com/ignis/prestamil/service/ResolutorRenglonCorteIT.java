package com.ignis.prestamil.service;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Totales del corte por día contra H2 (CAJ-01d): una cancelación cuenta el día en que se registra, no el
 * de la operación original (Q-C1, regla 1 de la sección 3 de PLAN-CORTE-CAJA).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ResolutorRenglonCorte.class)
class ResolutorRenglonCorteIT {

    private static final int SUCURSAL = 1;
    private static final LocalDate DIA_VENTA = LocalDate.of(2026, 10, 30);
    private static final LocalDate DIA_SIGUIENTE = DIA_VENTA.plusDays(1);

    @Autowired ResolutorRenglonCorte resolutor;
    @Autowired MovimientoCajaRepository movimientoCajaRepository;
    @Autowired TurnoRepository turnoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EntityManager em;

    private Usuario cajero;
    private Turno turno;

    @BeforeEach
    void sembrar() {
        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (3, 'Cajero', TRUE)").executeUpdate();
        Usuario u = new Usuario();
        u.setNombreUsuario("cajero");
        u.setPassword("x");
        u.setNombre("cajero");
        u.setApellidos("Prueba");
        u.setEstatus(true);
        u.setCreado(DIA_VENTA.atStartOfDay());
        u.setRol(em.find(Rol.class, 3));
        u.setVigencia(true);
        u.setFechaCambioPass(DIA_VENTA);
        cajero = usuarioRepository.save(u);

        Turno t = new Turno();
        t.setUsuario(cajero);
        t.setFechaInicio(DIA_VENTA.atTime(8, 0));
        t.setActivo(true);
        turno = turnoRepository.save(t);
    }

    @Test
    void ventaCanceladaAlDiaSiguiente_restaEnVentasDeEseDia() {
        flujo(ConceptoCaja.VENTA_VITRINA, "1000.00", "500.00", OrigenCaja.VENTA, 7L, DIA_VENTA.atTime(12, 0));
        flujo(ConceptoCaja.CANCELACION_VENTA, "1000.00", "500.00", OrigenCaja.CANCELACION_VENTA, 3L,
                DIA_SIGUIENTE.atTime(9, 30));
        // Otra sucursal no cuenta
        MovimientoCaja ajena = flujo(ConceptoCaja.VENTA_VITRINA, "50.00", "0.00", OrigenCaja.VENTA, 9L,
                DIA_SIGUIENTE.atTime(10, 0));
        ajena.setSucursalId(SUCURSAL + 1);
        em.flush();

        Map<RenglonCorte, ParteCorte> diaDeLaVenta = resolutor.totalesDelDia(SUCURSAL, DIA_VENTA);
        Map<RenglonCorte, ParteCorte> diaSiguiente = resolutor.totalesDelDia(SUCURSAL, DIA_SIGUIENTE);

        assertThat(diaDeLaVenta.get(RenglonCorte.VENTAS).efectivo()).isEqualByComparingTo("1000.00");
        assertThat(diaDeLaVenta.get(RenglonCorte.VENTAS).tarjeta()).isEqualByComparingTo("500.00");
        assertThat(diaSiguiente.get(RenglonCorte.VENTAS).efectivo()).isEqualByComparingTo("-1000.00");
        assertThat(diaSiguiente.get(RenglonCorte.VENTAS).tarjeta()).isEqualByComparingTo("-500.00");
    }

    @Test
    void elDiaIncluyeDeMedianocheAMedianoche() {
        flujo(ConceptoCaja.VENTA_VITRINA, "10.00", "0.00", OrigenCaja.VENTA, 1L, DIA_VENTA.atStartOfDay());
        flujo(ConceptoCaja.VENTA_VITRINA, "20.00", "0.00", OrigenCaja.VENTA, 2L, DIA_VENTA.atTime(23, 59, 59));
        flujo(ConceptoCaja.VENTA_VITRINA, "40.00", "0.00", OrigenCaja.VENTA, 3L, DIA_SIGUIENTE.atStartOfDay());
        em.flush();

        assertThat(resolutor.totalesDelDia(SUCURSAL, DIA_VENTA).get(RenglonCorte.VENTAS).efectivo())
                .isEqualByComparingTo("30.00");
    }

    private MovimientoCaja flujo(ConceptoCaja concepto, String efectivo, String tarjeta,
                                 OrigenCaja origenTipo, Long origenId, LocalDateTime fecha) {
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
        return movimientoCajaRepository.save(m);
    }
}
