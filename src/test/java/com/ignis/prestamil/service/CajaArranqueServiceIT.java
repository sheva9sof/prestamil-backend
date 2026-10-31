package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ConflictException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.model.CajaArranque;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaArranqueRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.FolioSecuenciaRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ArranqueCajaRequest;
import com.ignis.prestamil.request.FolioInicialRequest;
import com.ignis.prestamil.response.ArranqueCajaResponse;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranque;
import com.ignis.prestamil.service.calculo.SaldoArranqueCajaProvider;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Saldo de arranque de caja (CAJ-06) contra H2: Sistemas lo captura una sola vez con el último folio de
 * COCAE de cada serie, y la numeración de {@link FolioService} sigue desde ahí.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CajaArranqueService.class, FolioService.class, SaldoArranqueCajaProvider.class, CajaArranqueServiceIT.Reloj.class})
class CajaArranqueServiceIT {

    private static final int SUCURSAL = 1;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 31);

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(9, 0).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired CajaArranqueService cajaArranqueService;
    @Autowired FolioService folioService;
    @Autowired SaldoArranqueCajaProvider saldoArranqueCajaProvider;
    @Autowired CajaArranqueRepository cajaArranqueRepository;
    @Autowired FolioSecuenciaRepository folioSecuenciaRepository;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EntityManager em;

    @BeforeEach
    void sembrar() {
        usuario("sistemas", rol(1, "Sistemas"));
        usuario("gerente", rol(5, "Gerente"));
        Configuracion roles = new Configuracion();
        roles.setConfiguracion(Constantes.ROLES_PERMITIDOS_ARRANQUE_CAJA);
        roles.setValorCadena("1");
        configuracionRepository.save(roles);
    }

    @Test
    void arranqueConFolioInicial_laNumeracionSigueDesdeElUltimoDeCocae() {
        // Prestamil ya emitió 6 traspasos en pruebas; NOTA_VENTA no ha emitido ninguno
        contador(SerieFolio.TRASPASO_CAJA, 6L);

        ArranqueCajaResponse arranque = cajaArranqueService.capturar(SUCURSAL,
                request(HOY, "41013.11", SerieFolio.TRASPASO_CAJA, 4356L, SerieFolio.NOTA_VENTA, 9182L), "sistemas");

        assertThat(arranque.confirmado()).isTrue();
        assertThat(arranque.fecha()).isEqualTo(HOY);
        assertThat(arranque.saldoBoveda()).isEqualByComparingTo("41013.11");
        assertThat(arranque.usuarioCaptura()).isEqualTo("sistemas");
        assertThat(arranque.puedeCapturar()).isFalse();
        assertThat(arranque.folios()).containsExactlyInAnyOrder(
                new ArranqueCajaResponse.Folio(SerieFolio.TRASPASO_CAJA, 4356L),
                new ArranqueCajaResponse.Folio(SerieFolio.NOTA_VENTA, 9182L));
        assertThat(folioService.siguiente(SUCURSAL, SerieFolio.TRASPASO_CAJA)).isEqualTo(4357L);
        assertThat(folioService.siguiente(SUCURSAL, SerieFolio.NOTA_VENTA)).isEqualTo(9183L);
        // El corte toma el arranque capturado
        assertThat(saldoArranqueCajaProvider.saldoArranque(SUCURSAL))
                .contains(new SaldoArranque(HOY, new BigDecimal("41013.11")));
    }

    @Test
    void folioInicialMenorAlUltimoEmitido_400() {
        contador(SerieFolio.TRASPASO_CAJA, 6L);

        assertThatThrownBy(() -> cajaArranqueService.capturar(SUCURSAL,
                request(HOY, "1000.00", SerieFolio.TRASPASO_CAJA, 5L), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("TRASPASO_CAJA ya emitió hasta el folio 6");
    }

    @Test
    void folioInicialIgualAlUltimoEmitido_seAcepta() {
        contador(SerieFolio.TRASPASO_CAJA, 6L);

        cajaArranqueService.capturar(SUCURSAL, request(HOY, "1000.00", SerieFolio.TRASPASO_CAJA, 6L), "sistemas");

        assertThat(folioService.siguiente(SUCURSAL, SerieFolio.TRASPASO_CAJA)).isEqualTo(7L);
    }

    @Test
    void arranqueProvisional_seReemplazaAlCapturar_yDespuesYaNoCambia() {
        // Lo que pasó el changeset 086 desde configuraciones
        CajaArranque provisional = new CajaArranque();
        provisional.setSucursalId(SUCURSAL);
        provisional.setFechaArranque(HOY.minusDays(3));
        provisional.setSaldoBoveda(new BigDecimal("0.00"));
        cajaArranqueRepository.save(provisional);
        assertThat(cajaArranqueService.consultar(SUCURSAL, "sistemas").puedeCapturar()).isTrue();

        cajaArranqueService.capturar(SUCURSAL, request(HOY, "25000.00"), "sistemas");

        CajaArranque guardado = cajaArranqueRepository.findById(SUCURSAL).orElseThrow();
        assertThat(guardado.getConfirmado()).isTrue();
        assertThat(guardado.getFechaArranque()).isEqualTo(HOY);
        assertThat(guardado.getSaldoBoveda()).isEqualByComparingTo("25000.00");

        // Una sola vez
        assertThatThrownBy(() -> cajaArranqueService.capturar(SUCURSAL, request(HOY, "30000.00"), "sistemas"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya se capturó");
    }

    @Test
    void soloSistemas_403() {
        assertThatThrownBy(() -> cajaArranqueService.capturar(SUCURSAL, request(HOY, "1000.00"), "gerente"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(cajaArranqueService.consultar(SUCURSAL, "gerente").puedeCapturar()).isFalse();
    }

    @Test
    void fechaFutura_400() {
        assertThatThrownBy(() -> cajaArranqueService.capturar(SUCURSAL, request(HOY.plusDays(1), "1000.00"), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("posterior a hoy");
    }

    @Test
    void serieRepetida_400() {
        assertThatThrownBy(() -> cajaArranqueService.capturar(SUCURSAL,
                request(HOY, "1000.00", SerieFolio.NOTA_VENTA, 1L, SerieFolio.NOTA_VENTA, 2L), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("repetida");
    }

    /** Fecha, saldo y pares serie/último folio. */
    private static ArranqueCajaRequest request(LocalDate fecha, String saldo, Object... folios) {
        ArranqueCajaRequest r = new ArranqueCajaRequest();
        r.setFecha(fecha);
        r.setSaldoBoveda(new BigDecimal(saldo));
        for (int i = 0; i < folios.length; i += 2) {
            FolioInicialRequest f = new FolioInicialRequest();
            f.setSerie((SerieFolio) folios[i]);
            f.setUltimoFolio((Long) folios[i + 1]);
            r.getFolios().add(f);
        }
        return r;
    }

    private void contador(SerieFolio serie, Long ultimo) {
        FolioSecuencia f = new FolioSecuencia();
        f.setSucursalId(SUCURSAL);
        f.setSerie(serie);
        f.setUltimoFolio(ultimo);
        folioSecuenciaRepository.saveAndFlush(f);
    }

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
        usuarioRepository.save(u);
    }
}
