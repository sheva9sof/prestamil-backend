package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.request.CancelarMovimientoRequest;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * C-10: un cobro que hace commit mientras corre el pase de almoneda (o una cancelación) no se pierde.
 * Contra H2 con transacciones y bloqueos de fila reales: un hilo de "ventanilla" bloquea el contrato
 * con {@code findWithLockById} y le aplica el cobro, como {@code MovimientoContratoService.registrar};
 * el otro hilo ya hizo su lectura sin bloqueo y llega a releer el mismo contrato. La ventanilla hace
 * commit en cuanto H2 reporta una sesión formada detrás de su bloqueo, así que el cobro siempre cae
 * entre la primera lectura del otro hilo y su relectura.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaseAlmonedaService.class, MovimientoContratoService.class, PaseAlmonedaConcurrenciaIT.Reloj.class})
class PaseAlmonedaConcurrenciaIT {

    private static final int SUCURSAL = 1;
    private static final int ROL_GERENTE = 5;
    private static final int DIAS_GRACIA = 2;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(0, 5).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired PaseAlmonedaService paseAlmonedaService;
    @Autowired MovimientoContratoService movimientoContratoService;
    @Autowired ContratoRepository contratoRepository;
    @Autowired MovimientoContratoRepository movimientoContratoRepository;
    @Autowired PaseAlmonedaRepository paseAlmonedaRepository;
    @Autowired PaseAlmonedaDetalleRepository paseAlmonedaDetalleRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    // ejecutarPendientes(id) no usa SucursalService; cancelar() no cotiza ni cobra, y su caja se verifica aparte
    @MockBean SucursalService sucursalService;
    @MockBean CalculoContratoService calculoContratoService;
    @MockBean CobroService cobroService;
    @MockBean MovimientoCajaService movimientoCajaService;
    @MockBean ParametrosSistemaCache parametrosSistemaCache;

    // Ids sembrados; cada hilo los vuelve a leer en su propia transacción
    private Integer turnoId;
    private Integer gerenteId;
    private int clientes;

    @AfterEach
    void limpiar() {
        enTransaccion(() -> {
            for (String tabla : List.of("pase_almoneda_detalle", "pase_almoneda", "bitacora", "movimiento_contrato",
                    "contrato", "clientes", "direcciones", "plazo", "turnos", "usuarios", "roles", "configuraciones",
                    "sucursal")) {
                em.createNativeQuery("DELETE FROM " + tabla).executeUpdate();
            }
            return null;
        });
    }

    // =========================================================================
    // Pase de almoneda
    // =========================================================================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refrendoDuranteElPase_seConservaYElPaseNoLoPasaAVenta() throws Exception {
        Long[] ids = enTransaccion(() -> {
            sembrarBase();
            return new Long[]{
                    contrato("REFRENDA", EstatusContrato.VENCIDO, HOY.minusDays(15)).getId(),
                    contrato("SIN-COBRO", EstatusContrato.VENCIDO, HOY.minusDays(15)).getId()};
        });
        Long refrendado = ids[0];

        correrPaseConCobroEnCurso(refrendado, this::refrendar);

        Contrato c = contratoRepository.findById(refrendado).orElseThrow();
        assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VIGENTE);
        assertThat(c.getFechaVencimiento()).isEqualTo(HOY.plusDays(28));
        assertThat(c.getNumRefrendos()).isEqualTo(1);
        assertThat(tipos(refrendado)).containsExactly(TipoMovimiento.RF);
        assertThat(detalle()).extracting(d -> d.getContrato().getId()).doesNotContain(refrendado);
        // El otro contrato del mismo pase sí pasa a venta
        assertThat(contratoRepository.findById(ids[1]).orElseThrow().getEstatus()).isEqualTo(EstatusContrato.EN_VENTA);
        assertThat(tipos(ids[1])).containsExactly(TipoMovimiento.PV);
        assertThat(paseDeHoy().getContratosAVenta()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void finiquitoDuranteElPase_quedaFiniquitadoYElPaseLoIgnora() throws Exception {
        Long id = enTransaccion(() -> {
            sembrarBase();
            return contrato("FINIQUITA", EstatusContrato.VENCIDO, HOY.minusDays(15)).getId();
        });

        correrPaseConCobroEnCurso(id, this::finiquitar);

        Contrato c = contratoRepository.findById(id).orElseThrow();
        assertThat(c.getEstatus()).isEqualTo(EstatusContrato.FINIQUITADO);
        assertThat(c.getSaldoCapital()).isEqualByComparingTo("0");
        assertThat(tipos(id)).containsExactly(TipoMovimiento.FI);
        assertThat(detalle()).isEmpty();
        assertThat(paseDeHoy().getContratosAVenta()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void paseSinCobros_mismosResultadosQueAntes() {
        Long[] ids = enTransaccion(() -> {
            sembrarBase();
            return new Long[]{
                    contrato("EN-GRACIA", EstatusContrato.VIGENTE, HOY.minusDays(1)).getId(),
                    contrato("A-VENCIDO", EstatusContrato.VIGENTE, HOY.minusDays(3)).getId(),
                    contrato("A-VENTA", EstatusContrato.VENCIDO, HOY.minusDays(15)).getId()};
        });

        assertThat(paseAlmonedaService.ejecutarPendientes(SUCURSAL)).isEqualTo(1);

        assertThat(ids).extracting(id -> contratoRepository.findById(id).orElseThrow().getEstatus())
                .containsExactly(EstatusContrato.VIGENTE, EstatusContrato.VENCIDO, EstatusContrato.EN_VENTA);
        assertThat(tipos(ids[2])).containsExactly(TipoMovimiento.PV);
        assertThat(detalle()).extracting(d -> d.getContrato().getId(), PaseAlmonedaDetalle::getTipoCambio)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ids[1], TipoCambioPase.VENCIDO),
                        org.assertj.core.groups.Tuple.tuple(ids[2], TipoCambioPase.EN_VENTA));
        PaseAlmoneda pase = paseDeHoy();
        assertThat(pase.getContratosAVencido()).isEqualTo(1);
        assertThat(pase.getContratosAVenta()).isEqualTo(1);
        assertThat(pase.getMontoPasadoAVenta()).isEqualByComparingTo("1000.00");
    }

    // =========================================================================
    // Cancelación
    // =========================================================================

    /**
     * Doble cancelación del mismo refrendo (dos gerentes, o un doble clic): la segunda espera a que la
     * primera haga commit y la rechaza porque ya está cancelado, en vez de revertir y devolver dos veces.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelarConElContratoBloqueado_esperaElCommitYValidaContraElEstadoFresco() throws Exception {
        Long[] ids = enTransaccion(() -> {
            sembrarBase();
            Contrato c = contrato("CANCELA", EstatusContrato.VENCIDO, HOY.minusDays(10));
            refrendar(c);
            return new Long[]{c.getId(), movimientoContratoRepository.findByContratoIdOrderByFechaAsc(c.getId())
                    .get(0).getId()};
        });
        Long contratoId = ids[0];
        Long rfId = ids[1];

        CountDownLatch bloqueado = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Otro gerente ya está cancelando el refrendo: bloqueó el contrato y lo revirtió, falta el commit
            Future<Boolean> otraCancelacion = pool.submit(() -> cobroConBloqueo(contratoId, c -> {
                MovimientoContrato rf = em.find(MovimientoContrato.class, rfId);
                rf.setCancelado(true);
                rf.setMotivoCancelacion("Primera cancelación");
                c.setFechaVencimiento(rf.getFechaVencAnterior());
                c.setFechaComercializacion(rf.getFechaVencAnterior().plusDays(15));
                c.setEstatus(rf.getEstatusAnterior());
                c.setNumRefrendos(rf.getNumRefrendosAnterior());
            }, bloqueado));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> movimientoContratoService.cancelar(rfId, motivo(), "gerente"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("El movimiento ya está cancelado");
            assertThat(otraCancelacion.get(10, TimeUnit.SECONDS)).as("la cancelación esperó el bloqueo").isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Quedó la reversión de la primera; la segunda no tocó el contrato ni la caja
        Contrato c = contratoRepository.findById(contratoId).orElseThrow();
        assertThat(c.getEstatus()).isEqualTo(EstatusContrato.VENCIDO);
        assertThat(c.getFechaVencimiento()).isEqualTo(HOY.minusDays(10));
        assertThat(movimientoContratoRepository.findById(rfId).orElseThrow().getMotivoCancelacion())
                .isEqualTo("Primera cancelación");
        verify(movimientoCajaService, never()).registrar(any());
    }

    // =========================================================================
    // Concurrencia
    // =========================================================================

    /**
     * Corre el pase de hoy mientras la ventanilla tiene el contrato bloqueado con el cobro aplicado. La
     * preselección del pase no ve el cobro (sin commit); el commit llega cuando el pase ya espera en
     * la relectura.
     */
    private void correrPaseConCobroEnCurso(Long contratoId, Consumer<Contrato> cobro) throws Exception {
        CountDownLatch bloqueado = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> ventanilla = pool.submit(() -> cobroConBloqueo(contratoId, cobro, bloqueado));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(paseAlmonedaService.ejecutarPendientes(SUCURSAL)).isEqualTo(1);

            assertThat(ventanilla.get(10, TimeUnit.SECONDS)).as("el pase esperó al cobro").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Transacción de ventanilla: bloquea el contrato, aplica el cambio, avisa y hace commit en cuanto
     * otra sesión queda esperando ese bloqueo.
     *
     * @return si se vio a otra sesión esperando antes del commit
     */
    private boolean cobroConBloqueo(Long contratoId, Consumer<Contrato> cambio, CountDownLatch bloqueado) {
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(estado -> {
            Contrato c = contratoRepository.findWithLockById(contratoId).orElseThrow();
            cambio.accept(c);
            em.flush();
            bloqueado.countDown();
            return esperarSesionBloqueada();
        }));
    }

    /** Hasta 10 s a que H2 reporte una sesión detenida por un bloqueo de fila. */
    private boolean esperarSesionBloqueada() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer bloqueadas = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Integer.class);
            if (bloqueadas != null && bloqueadas > 0) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Lo que un refrendo deja en el contrato (MovimientoContratoService.aplicarAlContrato) y su RF. */
    private void refrendar(Contrato c) {
        MovimientoContrato rf = movimiento(c, TipoMovimiento.RF);
        c.setFechaContrato(HOY);
        c.setFechaVencimiento(HOY.plusDays(28));
        c.setFechaComercializacion(HOY.plusDays(28 + 15));
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setNumRefrendos(c.getNumRefrendos() + 1);
        rf.setFechaVencNueva(c.getFechaVencimiento());
        rf.setEstatusNuevo(c.getEstatus());
        em.persist(rf);
    }

    /** Lo que un finiquito deja en el contrato y su FI. */
    private void finiquitar(Contrato c) {
        MovimientoContrato fi = movimiento(c, TipoMovimiento.FI);
        c.setEstatus(EstatusContrato.FINIQUITADO);
        c.setSaldoCapital(BigDecimal.ZERO);
        fi.setSaldoNuevo(BigDecimal.ZERO);
        fi.setEstatusNuevo(EstatusContrato.FINIQUITADO);
        em.persist(fi);
    }

    private MovimientoContrato movimiento(Contrato c, TipoMovimiento tipo) {
        MovimientoContrato m = new MovimientoContrato();
        m.setContrato(c);
        m.setTurno(em.find(Turno.class, turnoId));
        m.setUsuario(em.find(Usuario.class, gerenteId));
        m.setTipo(tipo);
        m.setMonto(new BigDecimal("100.00"));
        m.setFecha(HOY.atTime(0, 1));
        m.setSaldoAnterior(c.getSaldoCapital());
        m.setSaldoNuevo(c.getSaldoCapital());
        m.setFechaContratoAnterior(c.getFechaContrato());
        m.setFechaVencAnterior(c.getFechaVencimiento());
        m.setEstatusAnterior(c.getEstatus());
        m.setNumRefrendosAnterior(c.getNumRefrendos());
        return m;
    }

    // =========================================================================
    // Datos
    // =========================================================================

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(transactionManager).execute(estado -> trabajo.get());
    }

    /** Sucursal, gerente con permiso de cancelar y su turno activo, plazo y cliente. */
    private void sembrarBase() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        sucursal.setFechaRegistroProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        sucursal.setFechaContratoProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        em.persist(sucursal);

        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (" + ROL_GERENTE + ", 'Gerente', TRUE)")
                .executeUpdate();
        Usuario gerente = new Usuario();
        gerente.setNombreUsuario("gerente");
        gerente.setPassword("x");
        gerente.setNombre("gerente");
        gerente.setApellidos("Prueba");
        gerente.setEstatus(true);
        gerente.setCreado(HOY.atStartOfDay());
        gerente.setRol(em.find(Rol.class, ROL_GERENTE));
        gerente.setVigencia(true);
        gerente.setFechaCambioPass(HOY);
        em.persist(gerente);
        gerenteId = gerente.getId();

        Configuracion permiso = new Configuracion();
        permiso.setConfiguracion("ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO");
        permiso.setValorCadena(String.valueOf(ROL_GERENTE));
        em.persist(permiso);

        Turno turno = new Turno();
        turno.setUsuario(gerente);
        turno.setFechaInicio(HOY.atStartOfDay());
        turno.setActivo(true);
        em.persist(turno);
        turnoId = turno.getId();
    }

    /** Contrato de 1,000 de la sucursal con su vencimiento y 2 días de gracia congelados. */
    private Contrato contrato(String folio, EstatusContrato estatus, LocalDate vencimiento) {
        Plazo plazo = new Plazo();
        plazo.setNombre("Mensual " + folio);
        plazo.setDiasPorPeriodo(28);
        plazo.setNumeroPeriodos(1);
        em.persist(plazo);

        Direccion d = new Direccion();
        d.setTipoDireccion(Direccion.TipoDireccion.Particular);
        d.setCalle("Calle");
        d.setNumeroExterior("1");
        d.setColonia("Centro");
        d.setCiudad("Pinotepa Nacional");
        d.setEstado("Oaxaca");
        d.setCodigoPostal("71600");
        d.setFechaRegistro(HOY);
        d.setEsVerificada(false);
        em.persist(d);

        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente");
        cliente.setApellidoPaterno(folio);
        cliente.setApellidoMaterno("Prueba");
        cliente.setTelefono(String.valueOf(9540000000L + ++clientes));
        cliente.setActivo(true);
        cliente.setDireccion(d);
        em.persist(cliente);

        Contrato c = new Contrato();
        c.setFolio(folio);
        c.setCliente(cliente);
        c.setTurno(em.find(Turno.class, turnoId));
        c.setUsuario(em.find(Usuario.class, gerenteId));
        c.setPlazo(plazo);
        c.setSucursalId(SUCURSAL);
        c.setFechaApertura(vencimiento.minusDays(28).atStartOfDay());
        c.setFechaContrato(vencimiento.minusDays(28));
        c.setFechaVencimiento(vencimiento);
        c.setFechaComercializacion(vencimiento.plusDays(15));
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setSaldoCapital(new BigDecimal("1000.00"));
        c.setMontoAvaluo(new BigDecimal("1300.00"));
        c.setEstatus(estatus);
        c.setSnapDiasGraciaSancion(DIAS_GRACIA);
        em.persist(c);
        return c;
    }

    private List<TipoMovimiento> tipos(Long contratoId) {
        return movimientoContratoRepository.findByContratoIdOrderByFechaAsc(contratoId).stream()
                .map(MovimientoContrato::getTipo).toList();
    }

    private List<PaseAlmonedaDetalle> detalle() {
        return enTransaccion(() -> {
            List<PaseAlmonedaDetalle> filas = paseAlmonedaDetalleRepository.findAll();
            filas.forEach(f -> f.getContrato().getId());
            return filas;
        });
    }

    private PaseAlmoneda paseDeHoy() {
        return paseAlmonedaRepository.findFirstBySucursalIdOrderByFechaDesc(SUCURSAL)
                .filter(p -> p.getFecha().equals(HOY)).orElseThrow();
    }

    private static CancelarMovimientoRequest motivo() {
        CancelarMovimientoRequest r = new CancelarMovimientoRequest();
        r.setMotivo("Cancelación repetida por doble clic");
        return r;
    }
}
