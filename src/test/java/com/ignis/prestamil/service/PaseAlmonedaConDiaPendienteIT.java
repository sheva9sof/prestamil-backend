package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.mapper.TurnoMapperImpl;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.CajaArranque;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranqueCajaProvider;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CAJ-06 y H-06: un día pendiente de cierre bloquea la apertura de turno, pero no el pase de almoneda, que
 * corre al arrancar y a las 00:05 sin depender del turno ni de la caja. Contra H2 con transacciones reales,
 * porque el pase corre cada día en su propia transacción.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaseAlmonedaService.class, TurnoService.class, TurnoMapperImpl.class, CierreCajaService.class,
        CorteCajaService.class, ResolutorRenglonCorte.class, SaldoArranqueCajaProvider.class,
        PaseAlmonedaConDiaPendienteIT.Reloj.class})
class PaseAlmonedaConDiaPendienteIT {

    private static final int SUCURSAL = 1;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(0, 5).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired PaseAlmonedaService paseAlmonedaService;
    @Autowired TurnoService turnoService;
    @Autowired CierreCajaService cierreCajaService;
    @Autowired PaseAlmonedaRepository paseAlmonedaRepository;
    @Autowired TurnoRepository turnoRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    @MockBean SucursalService sucursalService;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            for (String tabla : List.of("pase_almoneda", "traspaso_caja", "caja", "caja_arranque", "turnos",
                    "usuarios", "roles", "sucursal")) {
                em.createNativeQuery("DELETE FROM " + tabla).executeUpdate();
            }
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void diaPendiente_bloqueaElTurno_peroElPaseDeAlmonedaSigueCorriendo() {
        Sucursal sucursal = new TransactionTemplate(transactionManager).execute(estado -> sembrar());
        when(sucursalService.findUnique()).thenReturn(Optional.of(sucursal));

        // Ayer hubo una dotación y nadie cerró el día
        assertThat(cierreCajaService.diaPendiente(SUCURSAL)).contains(HOY.minusDays(1));

        // No se abre turno...
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cajero", null, List.of()));
        assertThatThrownBy(() -> turnoService.iniciarTurno())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pendiente de cierre");
        assertThat(turnoRepository.findByActivo(true)).isEmpty();

        // ...pero el pase de las 00:05 pone al día ayer y hoy
        new PaseAlmonedaProgramador(paseAlmonedaService).diario();

        assertThat(paseAlmonedaRepository.findAll())
                .extracting(PaseAlmoneda::getFecha)
                .contains(HOY.minusDays(1), HOY);
    }

    /** Sucursal con su último pase hace dos días, arranque confirmado y una dotación de ayer sin cerrar. */
    private Sucursal sembrar() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        sucursal.setFechaRegistroProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        sucursal.setFechaContratoProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        em.persist(sucursal);

        PaseAlmoneda pase = new PaseAlmoneda();
        pase.setSucursalId(SUCURSAL);
        pase.setFecha(HOY.minusDays(2));
        pase.setEjecutadoEn(HOY.minusDays(2).atStartOfDay());
        pase.setContratosAVencido(0);
        pase.setContratosAVenta(0);
        pase.setMontoPasadoAVenta(BigDecimal.ZERO);
        em.persist(pase);

        CajaArranque arranque = new CajaArranque();
        arranque.setSucursalId(SUCURSAL);
        arranque.setFechaArranque(HOY.minusDays(3));
        arranque.setSaldoBoveda(new BigDecimal("10000.00"));
        arranque.setConfirmado(true);
        em.persist(arranque);

        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (3, 'Cajero', TRUE)").executeUpdate();
        Usuario cajero = new Usuario();
        cajero.setNombreUsuario("cajero");
        cajero.setPassword("x");
        cajero.setNombre("cajero");
        cajero.setApellidos("Prueba");
        cajero.setEstatus(true);
        cajero.setCreado(HOY.atStartOfDay());
        cajero.setRol(em.find(Rol.class, 3));
        cajero.setVigencia(true);
        cajero.setFechaCambioPass(HOY);
        em.persist(cajero);

        TraspasoCaja dotacion = new TraspasoCaja();
        dotacion.setSucursalId(SUCURSAL);
        dotacion.setFolio(1L);
        dotacion.setTipo(TipoTraspasoCaja.DOTACION);
        dotacion.setCajaOrigen(caja(TipoCaja.BOVEDA, "002"));
        dotacion.setCajaDestino(caja(TipoCaja.VENTANILLA, "003"));
        dotacion.setImporte(new BigDecimal("1000.00"));
        dotacion.setFechaOperacion(HOY.minusDays(1));
        dotacion.setFechaRegistro(HOY.minusDays(1).atTime(9, 0));
        dotacion.setUsuario(cajero);
        em.persist(dotacion);
        return sucursal;
    }

    private Caja caja(TipoCaja tipo, String clave) {
        Caja c = new Caja();
        c.setSucursalId(SUCURSAL);
        c.setTipo(tipo);
        c.setClave(clave);
        c.setNombre(tipo.name());
        em.persist(c);
        return c;
    }
}
