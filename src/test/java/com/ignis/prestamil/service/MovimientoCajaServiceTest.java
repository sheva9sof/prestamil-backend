package com.ignis.prestamil.service;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoMovimientoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Escritura de {@code movimiento_caja} con concepto estructurado e importes separados (CAJ-01b).
 */
@ExtendWith(MockitoExtension.class)
class MovimientoCajaServiceTest {

    @Mock MovimientoCajaRepository repository;
    @Mock MovimientoContratoRepository movimientoContratoRepository;

    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 10, 30, 11, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private MovimientoCajaService service;
    private Turno turno;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new MovimientoCajaService(repository, movimientoContratoRepository, clock);
        lenient().when(repository.save(any(MovimientoCaja.class))).thenAnswer(inv -> inv.getArgument(0));
        turno = new Turno();
        turno.setId(7);
        usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
    }

    /** Aceptación de CAJ-01b: refrendo pagado mitad con tarjeta y mitad en efectivo, con cambio. */
    @Test
    void refrendoMixto_guardaEfectivoNetoDelCambioYTarjetaPorSeparado() {
        MovimientoContrato rf = new MovimientoContrato();
        rf.setId(55L);
        rf.setTipo(TipoMovimiento.RF);
        rf.setMonto(new BigDecimal("100.00"));
        // Total 100: 50 con tarjeta y un billete de 100 → 50 de cambio
        rf.setImporteTarjeta(new BigDecimal("50.00"));
        rf.setImporteEfectivo(new BigDecimal("100.00"));
        rf.setCambioEntregado(new BigDecimal("50.00"));
        when(movimientoContratoRepository.getReferenceById(55L)).thenReturn(rf);

        MovimientoCaja m = service.registrar(RegistroCaja.deMovimientoContrato(
                rf, ConceptoCaja.deMovimiento(rf.getTipo()), turno, 1, usuario, "COBRO REFRENDO FOLIO 9"));

        assertThat(m.getConceptoCaja()).isEqualTo(ConceptoCaja.REFRENDO);
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoCaja.ENTRADA);
        assertThat(m.getImporteEfectivo()).isEqualByComparingTo("50.00");
        assertThat(m.getImporteTarjeta()).isEqualByComparingTo("50.00");
        assertThat(m.getMonto()).isEqualByComparingTo("100.00");
        assertThat(m.getOrigenTipo()).isEqualTo(OrigenCaja.MOVIMIENTO_CONTRATO);
        assertThat(m.getOrigenId()).isEqualTo(55L);
        assertThat(m.getMovimientoContrato()).isSameAs(rf);
        assertThat(m.getTurno()).isSameAs(turno);
        assertThat(m.getSucursalId()).isEqualTo(1);
        assertThat(m.getUsuario()).isSameAs(usuario);
        assertThat(m.getFecha()).isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 0));
    }

    /** El EMP no pasa por la ventana de Cobro: el préstamo sale completo en efectivo. */
    @Test
    void empenoSinDatosDePago_vaCompletoEnEfectivo() {
        MovimientoContrato emp = new MovimientoContrato();
        emp.setId(10L);
        emp.setTipo(TipoMovimiento.EMP);
        emp.setMonto(new BigDecimal("1195.00"));
        when(movimientoContratoRepository.getReferenceById(10L)).thenReturn(emp);

        MovimientoCaja m = service.registrar(RegistroCaja.deMovimientoContrato(
                emp, ConceptoCaja.deMovimiento(emp.getTipo()), turno, 1, usuario, "PRÉSTAMO CONTRATO CTR-000010"));

        assertThat(m.getConceptoCaja()).isEqualTo(ConceptoCaja.EMPENO);
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoCaja.SALIDA);
        assertThat(m.getImporteEfectivo()).isEqualByComparingTo("1195.00");
        assertThat(m.getImporteTarjeta()).isEqualByComparingTo("0");
        assertThat(m.getMonto()).isEqualByComparingTo("1195.00");
    }

    /** Ventas (A-03): origen sin FK, no se liga a movimiento_contrato. */
    @Test
    void ventaDeVitrina_guardaOrigenSinLigarMovimientoDeContrato() {
        MovimientoCaja m = service.registrar(new RegistroCaja(turno, 1, ConceptoCaja.VENTA_VITRINA,
                new BigDecimal("500.00"), new BigDecimal("1000.00"), OrigenCaja.VENTA, 900L, usuario,
                "VENTA NOTA 1"));

        assertThat(m.getTipo()).isEqualTo(TipoMovimientoCaja.ENTRADA);
        assertThat(m.getMonto()).isEqualByComparingTo("1500.00");
        assertThat(m.getOrigenTipo()).isEqualTo(OrigenCaja.VENTA);
        assertThat(m.getOrigenId()).isEqualTo(900L);
        assertThat(m.getMovimientoContrato()).isNull();
        verify(movimientoContratoRepository, never()).getReferenceById(anyLong());
    }

    @Test
    void cancelacionDeVenta_esSalida() {
        MovimientoCaja m = service.registrar(new RegistroCaja(turno, 1, ConceptoCaja.CANCELACION_VENTA,
                new BigDecimal("500.00"), BigDecimal.ZERO, OrigenCaja.CANCELACION_VENTA, 3L, usuario,
                "CANCELACIÓN VENTA NOTA 1"));

        assertThat(m.getTipo()).isEqualTo(TipoMovimientoCaja.SALIDA);
    }

    @Test
    void descripcionLarga_seRecortaA120() {
        MovimientoCaja m = service.registrar(new RegistroCaja(turno, 1, ConceptoCaja.VENTA_FUNDICION,
                BigDecimal.TEN, BigDecimal.ZERO, OrigenCaja.VENTA, 1L, usuario, "X".repeat(200)));

        assertThat(m.getConcepto()).hasSize(120);
    }

    @Test
    void origenIncompleto_seRechaza() {
        RegistroCaja sinId = new RegistroCaja(turno, 1, ConceptoCaja.VENTA_VITRINA,
                BigDecimal.TEN, BigDecimal.ZERO, OrigenCaja.VENTA, null, usuario, "VENTA");

        assertThatThrownBy(() -> service.registrar(sinId)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void importesNegativosOEnCero_noFormanUnRegistro() {
        assertThatThrownBy(() -> new RegistroCaja(turno, 1, ConceptoCaja.VENTA_VITRINA,
                new BigDecimal("-1"), BigDecimal.ZERO, OrigenCaja.VENTA, 1L, usuario, "VENTA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroCaja(turno, 1, ConceptoCaja.VENTA_VITRINA,
                BigDecimal.ZERO, BigDecimal.ZERO, OrigenCaja.VENTA, 1L, usuario, "VENTA"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
