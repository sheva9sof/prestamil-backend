package com.ignis.prestamil.service;

import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.PaseAlmonedaRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puesta al dia del pase de almoneda contra H2, con transacciones y bloqueo de fila reales: dos
 * ejecuciones simultaneas de la misma sucursal no repiten ningun dia ni fallan.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaseAlmonedaService.class, PaseAlmonedaServiceIT.Reloj.class})
class PaseAlmonedaServiceIT {

    private static final int SUCURSAL = 1;
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
    @Autowired PaseAlmonedaRepository paseAlmonedaRepository;
    @Autowired SucursalRepository sucursalRepository;
    // Solo lo usa ejecutarPendientes() sin argumentos; aqui se pasa la sucursal explicita.
    @MockBean SucursalService sucursalService;

    @AfterEach
    void limpiar() {
        paseAlmonedaRepository.deleteAll();
        sucursalRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosEjecucionesSimultaneas_cadaDiaCorreUnaSolaVez() throws Exception {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        sucursal.setFechaRegistroProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        sucursal.setFechaContratoProfeco(LocalDateTime.of(2020, 1, 1, 0, 0));
        sucursalRepository.saveAndFlush(sucursal);
        paseAlmonedaRepository.saveAndFlush(pase(HOY.minusDays(10)));

        CountDownLatch salida = new CountDownLatch(1);
        Callable<Integer> tarea = () -> {
            salida.await();
            return paseAlmonedaService.ejecutarPendientes(SUCURSAL);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> hilo1 = pool.submit(tarea);
            Future<Integer> hilo2 = pool.submit(tarea);
            salida.countDown();

            // Entre los dos corrieron exactamente los 10 dias faltantes
            int total = hilo1.get(30, TimeUnit.SECONDS) + hilo2.get(30, TimeUnit.SECONDS);
            assertThat(total).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        assertThat(paseAlmonedaRepository.findAll())
                .extracting(PaseAlmoneda::getFecha)
                .hasSize(11)
                .doesNotHaveDuplicates()
                .contains(HOY.minusDays(9), HOY);
        // Ya al dia: una tercera llamada no hace nada
        assertThat(paseAlmonedaService.ejecutarPendientes(SUCURSAL)).isZero();
    }

    private static PaseAlmoneda pase(LocalDate fecha) {
        PaseAlmoneda p = new PaseAlmoneda();
        p.setSucursalId(SUCURSAL);
        p.setFecha(fecha);
        p.setEjecutadoEn(fecha.atStartOfDay());
        p.setContratosAVencido(0);
        p.setContratosAVenta(0);
        p.setMontoPasadoAVenta(BigDecimal.ZERO);
        return p;
    }
}
