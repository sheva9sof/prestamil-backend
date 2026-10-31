package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ConflictException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.mapper.CajaMapperImpl;
import com.ignis.prestamil.model.CajaArranque;
import com.ignis.prestamil.model.CierreCajaDiario;
import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaArranqueRepository;
import com.ignis.prestamil.repository.CierreCajaDiarioRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.DenominacionRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.request.CancelarTraspasoCajaRequest;
import com.ignis.prestamil.request.TraspasoCajaDenominacionRequest;
import com.ignis.prestamil.request.TraspasoCajaRequest;
import com.ignis.prestamil.response.CierreCajaResponse;
import com.ignis.prestamil.service.calculo.CorteCajaJson;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaSemilla;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranqueCajaProvider;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.TipoDenominacion;
import com.ignis.prestamil.util.Constantes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cierre de día de caja (CAJ-06) contra H2, con el motor del corte, los traspasos y el registro de caja
 * reales. Reglas de Jorge (B-04): el gerente cierra el día en curso, un día pendiente solo lo cierra
 * Sistemas y mientras haya uno no se abre turno. Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CierreCajaService.class, CandadoCierreCaja.class, CorteCajaService.class, ResolutorRenglonCorte.class,
        SaldoArranqueCajaProvider.class, CorteCajaConsultaService.class, MovimientoCajaService.class,
        TraspasoCajaService.class, SaldoCajaService.class, FolioService.class, CajaMapperImpl.class,
        CierreCajaServiceIT.Reloj.class})
class CierreCajaServiceIT {

    private static final int SUCURSAL = CorteCajaSemilla.SUCURSAL;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    private static final LocalDate AYER = HOY.minusDays(1);

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(18, 0).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired CierreCajaService cierreCajaService;
    @Autowired CorteCajaService corteCajaService;
    @Autowired CorteCajaConsultaService corteCajaConsultaService;
    @Autowired MovimientoCajaService movimientoCajaService;
    @Autowired TraspasoCajaService traspasoCajaService;
    @Autowired CierreCajaDiarioRepository cierreCajaDiarioRepository;
    @Autowired CajaArranqueRepository cajaArranqueRepository;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired DenominacionRepository denominacionRepository;
    @Autowired TurnoRepository turnoRepository;
    @Autowired EntityManager em;

    private CorteCajaSemilla semilla;
    private Turno turno;
    private Usuario cajero;
    private Denominacion billeteDeMil;

    @BeforeEach
    void sembrar() {
        semilla = new CorteCajaSemilla(em, configuracionRepository);
        turno = turnoRepository.findByActivo(true).orElseThrow();
        cajero = turno.getUsuario();
        // Por defecto no hay turno abierto: cerrar el día lo exige
        turno.setActivo(false);

        usuario("gerente", rol(5, "Gerente"));
        usuario("sistemas", rol(1, "Sistemas"));
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA, "5,1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE, "1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA, "5,1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_DOTACION, "5,3,1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CANCELAR_TRASPASO, "1");

        billeteDeMil = new Denominacion();
        billeteDeMil.setValor(new BigDecimal("1000.00"));
        billeteDeMil.setTipo(TipoDenominacion.BILLETE);
        billeteDeMil.setOrden(1);
        billeteDeMil.setActiva(true);
        denominacionRepository.save(billeteDeMil);
    }

    @Test
    void cierreNormal_elGerenteCierraHoy_congelaElCorteYSusCifras() {
        semilla.arranque(HOY, "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "3000.00", HOY);
        semilla.flujo(ConceptoCaja.EMPENO, "1000.00", "0.00", null, null, HOY.atTime(11, 0));
        em.flush();
        CorteCajaResultado calculado = corteCajaService.calcular(SUCURSAL, HOY);

        CierreCajaResponse cierre = cierreCajaService.cerrar(SUCURSAL, null, "gerente");

        assertThat(cierre.fecha()).isEqualTo(HOY);
        assertThat(cierre.diaPendiente()).isFalse();
        assertThat(cierre.usuario()).isEqualTo("gerente");
        // Bóveda: 10,000 − 3,000 de dotación; gaveta: 3,000 − 1,000 del empeño
        assertThat(cierre.saldoFinal()).isEqualByComparingTo("7000.00");
        assertThat(cierre.diferencia()).isEqualByComparingTo("2000.00");

        CierreCajaDiario guardado = cierreCajaDiarioRepository.findBySucursalIdAndFecha(SUCURSAL, HOY).orElseThrow();
        assertThat(guardado.getSaldoAnterior()).isEqualByComparingTo("10000.00");
        assertThat(guardado.getDotaciones()).isEqualByComparingTo("3000.00");
        assertThat(guardado.getEgresosEfectivo()).isEqualByComparingTo("1000.00");
        assertThat(CorteCajaJson.leer(guardado.getCorteJson())).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(calculado);
        assertThat(cajaArranqueRepository.findById(SUCURSAL).orElseThrow().getFechaUltimoCierre()).isEqualTo(HOY);

        // La pantalla del corte lo muestra cerrado
        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, HOY, "gerente");
        assertThat(pantalla.cierre()).isNotNull();
        assertThat(pantalla.puedeCerrar()).isFalse();
    }

    @Test
    void dobleCierre_409() {
        semilla.arranque(HOY, "10000.00");
        cierreCajaService.cerrar(SUCURSAL, HOY, "gerente");

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY, "sistemas"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("31/10/2026 ya se cerró")
                .hasMessageContaining("gerente");
        assertThat(cierreCajaDiarioRepository.count()).isEqualTo(1);
    }

    @Test
    void cierreConTurnoAbierto_400_primeroSeCierraElTurno() {
        semilla.arranque(HOY, "10000.00");
        turno.setActivo(true);

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY, "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Primero cierra el turno")
                .hasMessageContaining("turno abierto");
        assertThat(cierreCajaDiarioRepository.count()).isZero();

        // La pantalla deja el botón, pero dice por qué no se puede todavía
        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, HOY, "gerente");
        assertThat(pantalla.puedeCerrar()).isTrue();
        assertThat(pantalla.avisoCierre()).startsWith("Primero cierra el turno");
    }

    @Test
    void pantalla_elCajeroNuncaPuedeCerrar() {
        semilla.arranque(HOY, "10000.00");

        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, HOY, cajero.getNombreUsuario());

        assertThat(pantalla.puedeCerrar()).isFalse();
        assertThat(pantalla.puedeCerrarPendiente()).isFalse();
    }

    @Test
    void pantalla_unDiaPasadoSinMovimientosNoOfreceCerrar_niASistemas() {
        semilla.arranque(AYER, "10000.00");

        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, AYER, "sistemas");

        assertThat(pantalla.diaPendiente()).isFalse();
        assertThat(pantalla.puedeCerrar()).isFalse();
        assertThat(pantalla.primerDiaPendiente()).isNull();
    }

    @Test
    void pantalla_avisaDelDiaPendienteAunqueSeVeaHoy_ySoloSistemasPuedeIrACerrarlo() {
        semilla.arranque(AYER.minusDays(1), "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "500.00", AYER);
        em.flush();

        var gerente = corteCajaConsultaService.consultar(SUCURSAL, HOY, "gerente");
        assertThat(gerente.primerDiaPendiente()).isEqualTo(AYER);
        assertThat(gerente.puedeCerrarPendiente()).isFalse();
        // Hoy sigue con su botón, pero no se cierra antes que el pendiente
        assertThat(gerente.puedeCerrar()).isTrue();
        assertThat(gerente.avisoCierre()).contains("Primero hay que cerrar el 30/10/2026");

        var sistemas = corteCajaConsultaService.consultar(SUCURSAL, HOY, "sistemas");
        assertThat(sistemas.primerDiaPendiente()).isEqualTo(AYER);
        assertThat(sistemas.puedeCerrarPendiente()).isTrue();

        var pendiente = corteCajaConsultaService.consultar(SUCURSAL, AYER, "sistemas");
        assertThat(pendiente.diaPendiente()).isTrue();
        assertThat(pendiente.puedeCerrar()).isTrue();
        assertThat(pendiente.avisoCierre()).isNull();
    }

    @Test
    void pantalla_unDiaCongeladoPorUnCierrePosteriorSeVeCerrado() {
        semilla.arranque(AYER.minusDays(1), "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "500.00", HOY);
        em.flush();
        cierreCajaService.cerrar(SUCURSAL, HOY, "gerente");

        // Ayer no tuvo movimientos ni cierre propio, pero ya quedó congelado
        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, AYER, "sistemas");
        assertThat(pantalla.cierre()).isNull();
        assertThat(pantalla.cerrado()).isTrue();
        assertThat(pantalla.puedeCerrar()).isFalse();

        var hoy = corteCajaConsultaService.consultar(SUCURSAL, HOY, "gerente");
        assertThat(hoy.cerrado()).isTrue();
        assertThat(hoy.cierre().usuario()).isEqualTo("gerente");
    }

    @Test
    void diaPendiente_bloqueaLaAperturaDeTurno() {
        semilla.arranque(AYER.minusDays(1), "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "500.00", AYER);
        em.flush();

        assertThat(cierreCajaService.diaPendiente(SUCURSAL)).contains(AYER);
        assertThat(cierreCajaService.motivoBloqueoTurno(SUCURSAL)).hasValueSatisfying(motivo ->
                assertThat(motivo).contains("30/10/2026 quedó pendiente de cierre"));
        assertThatThrownBy(() -> cierreCajaService.validarAperturaTurno(SUCURSAL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("La caja del 30/10/2026 quedó pendiente de cierre")
                .hasMessageContaining("Sistemas");
    }

    @Test
    void diaSinMovimientos_noQuedaPendiente() {
        semilla.arranque(AYER.minusDays(1), "10000.00");

        assertThat(cierreCajaService.diaPendiente(SUCURSAL)).isEmpty();
        assertThat(cierreCajaService.motivoBloqueoTurno(SUCURSAL)).isEmpty();
        assertThatCode(() -> cierreCajaService.validarAperturaTurno(SUCURSAL)).doesNotThrowAnyException();
    }

    @Test
    void gerenteIntentaCerrarUnDiaPendiente_403() {
        semilla.arranque(AYER, "10000.00");
        semilla.flujo(ConceptoCaja.EMPENO, "1000.00", "0.00", null, null, AYER.atTime(12, 0));

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, AYER, "gerente"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("solo Sistemas puede cerrarlo");
        assertThat(cierreCajaDiarioRepository.count()).isZero();

        // En pantalla el gerente lo ve pendiente y sin botón
        var pantalla = corteCajaConsultaService.consultar(SUCURSAL, AYER, "gerente");
        assertThat(pantalla.diaPendiente()).isTrue();
        assertThat(pantalla.puedeCerrar()).isFalse();
    }

    @Test
    void sistemasCierraElDiaPendiente_yYaSePuedeAbrirTurno() {
        semilla.arranque(AYER, "10000.00");
        semilla.flujo(ConceptoCaja.EMPENO, "1000.00", "0.00", null, null, AYER.atTime(12, 0));
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "2000.00", AYER);

        CierreCajaResponse cierre = cierreCajaService.cerrar(SUCURSAL, AYER, "sistemas");

        assertThat(cierre.diaPendiente()).isTrue();
        assertThat(cierre.saldoFinal()).isEqualByComparingTo("8000.00");
        assertThat(cierreCajaService.diaPendiente(SUCURSAL)).isEmpty();
        assertThatCode(() -> cierreCajaService.validarAperturaTurno(SUCURSAL)).doesNotThrowAnyException();
    }

    @Test
    void hoyNoSeCierraMientrasUnDiaAnteriorSigaPendiente() {
        semilla.arranque(AYER, "10000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "2000.00", AYER);

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY, "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Primero hay que cerrar el 30/10/2026");
    }

    @Test
    void sinArranqueCapturadoPorSistemas_noSeCierraNiSeBloqueaElTurno() {
        CajaArranque provisional = semilla.arranque(AYER, "0.00");
        provisional.setConfirmado(false);
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "0.00", AYER);

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY, "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("capturar el saldo de arranque");
        assertThat(cierreCajaService.diaPendiente(SUCURSAL)).isEmpty();
        assertThatCode(() -> cierreCajaService.validarAperturaTurno(SUCURSAL)).doesNotThrowAnyException();
    }

    @Test
    void diaCerrado_rechazaTraspasosCobrosEmpenosYCancelaciones() {
        semilla.arranque(HOY, "10000.00");
        TraspasoCaja dotacion = semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", HOY);
        cierreCajaService.cerrar(SUCURSAL, HOY, "gerente");
        String mensaje = "La caja del 31/10/2026 ya se cerró";

        // Traspaso
        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL, dotacion(), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(mensaje);
        // Cancelación de traspaso
        CancelarTraspasoCajaRequest motivo = new CancelarTraspasoCajaRequest();
        motivo.setMotivo("Se capturó el importe equivocado");
        assertThatThrownBy(() -> traspasoCajaService.cancelar(dotacion.getId(), motivo, "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(mensaje);
        // Empeños, cobros y cancelaciones de contrato pasan por el registro de caja
        for (ConceptoCaja concepto : List.of(ConceptoCaja.EMPENO, ConceptoCaja.REFRENDO, ConceptoCaja.DEVOLUCION_COBRO)) {
            assertThatThrownBy(() -> movimientoCajaService.registrar(new RegistroCaja(turno, SUCURSAL, concepto,
                    new BigDecimal("100.00"), BigDecimal.ZERO, null, null, cajero, concepto.name())))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining(mensaje);
        }
        // Y hoy ya no se abre turno
        assertThatThrownBy(() -> cierreCajaService.validarAperturaTurno(SUCURSAL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya se cerró");
        assertThat(traspasoCajaService.contexto(SUCURSAL, TipoTraspasoCaja.DOTACION, "cajero").isDiaCerrado()).isTrue();
        em.flush();
        em.clear();
        assertThat(em.createQuery("SELECT COUNT(m) FROM MovimientoCaja m", Long.class).getSingleResult()).isZero();
    }

    @Test
    void diaAbierto_elRegistroDeCajaSiPasa() {
        semilla.arranque(HOY, "10000.00");

        MovimientoCaja m = movimientoCajaService.registrar(new RegistroCaja(turno, SUCURSAL, ConceptoCaja.EMPENO,
                new BigDecimal("100.00"), BigDecimal.ZERO, null, null, cajero, "EMPEÑO"));

        assertThat(m.getId()).isNotNull();
    }

    @Test
    void saldoAnterior_saleDelUltimoCierre_noDeRecalcularDesdeElArranque() {
        semilla.arranque(AYER, "10000.00");
        semilla.traspaso(TipoTraspasoCaja.MINISTRACION, "5000.00", AYER);
        cierreCajaService.cerrar(SUCURSAL, AYER, "sistemas");

        // Un traspaso que aparece en un día ya cerrado (p. ej. un ajuste directo en la base) no cambia
        // ni el corte congelado de ese día ni el saldo que se arrastra
        semilla.traspaso(TipoTraspasoCaja.MINISTRACION, "999.00", AYER);
        em.flush();

        CorteCajaResultado ayer = corteCajaService.calcular(SUCURSAL, AYER);
        CorteCajaResultado hoy = corteCajaService.calcular(SUCURSAL, HOY);
        assertThat(ayer.ministraciones()).isEqualByComparingTo("5000.00");
        assertThat(ayer.saldoFinal()).isEqualByComparingTo("15000.00");
        assertThat(hoy.saldoAnterior()).isEqualByComparingTo("15000.00");

        // El rango arrastra igual: el día cerrado congelado y el siguiente desde su saldo final
        List<CorteCajaResultado> rango = corteCajaService.calcularRango(SUCURSAL, AYER, HOY);
        assertThat(rango).extracting(CorteCajaResultado::saldoAnterior)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10000.00"), new BigDecimal("15000.00"));
    }

    @Test
    void diaFuturoOAnteriorAlArranque_400() {
        semilla.arranque(HOY, "10000.00");

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY.plusDays(1), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("día futuro");
        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, AYER, "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("anterior al arranque");
    }

    @Test
    void cajero_noCierraElDia_403() {
        semilla.arranque(HOY, "10000.00");

        assertThatThrownBy(() -> cierreCajaService.cerrar(SUCURSAL, HOY, "cajero"))
                .isInstanceOf(ForbiddenException.class);
    }

    private TraspasoCajaRequest dotacion() {
        TraspasoCajaDenominacionRequest renglon = new TraspasoCajaDenominacionRequest();
        renglon.setDenominacionId(billeteDeMil.getId());
        renglon.setCantidad(1);
        TraspasoCajaRequest request = new TraspasoCajaRequest();
        request.setTipo(TipoTraspasoCaja.DOTACION);
        request.setImporte(new BigDecimal("1000.00"));
        request.setDesglose(List.of(renglon));
        return request;
    }

    /** Rol con el id fijo de 002-initial-data, para que las configuraciones sean las de 086. */
    private Rol rol(int id, String nombre) {
        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (?1, ?2, TRUE)")
                .setParameter(1, id)
                .setParameter(2, nombre)
                .executeUpdate();
        return em.find(Rol.class, id);
    }

    private void usuario(String nombreUsuario, Rol rol) {
        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario);
        u.setPassword("x");
        u.setNombre(nombreUsuario);
        u.setApellidos("Prueba");
        u.setEstatus(true);
        u.setCreado(HOY.atStartOfDay());
        u.setRol(rol);
        u.setVigencia(true);
        u.setFechaCambioPass(HOY);
        em.persist(u);
    }
}
