package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TurnoRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    TurnoRepository turnoRepository;

    // -----------------------------------------------------------------------
    // Helper: persist a minimal Rol
    // -----------------------------------------------------------------------

    private Rol persistRol(String nombreRol) {
        Rol rol = new Rol();
        rol.setRol(nombreRol);
        rol.setEstatus(true);
        return em.persistAndFlush(rol);
    }

    // -----------------------------------------------------------------------
    // Helper: persist a minimal Usuario (satisfies all nullable=false columns)
    // -----------------------------------------------------------------------

    private Usuario persistUsuario(String nombreUsuario) {
        Rol rol = persistRol("ROL_" + nombreUsuario);

        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario);
        u.setNombre("Nombre");
        u.setApellidos("Apellido");
        u.setPassword("$2a$10$hashplaceholder");
        u.setEstatus(true);
        u.setCambiarPassword(false);
        u.setCreado(LocalDateTime.now());
        u.setRol(rol);
        u.setVigencia(true);
        u.setAplicaCambioPassword(false);
        u.setFechaCambioPass(LocalDate.now());
        u.setEditable(true);
        return em.persistAndFlush(u);
    }

    // -----------------------------------------------------------------------
    // Helper: create and persist a Turno
    // -----------------------------------------------------------------------

    private Turno persistTurno(Usuario usuario, boolean activo) {
        Turno t = new Turno();
        t.setUsuario(usuario);
        t.setFechaInicio(LocalDateTime.now().minusHours(2));
        t.setActivo(activo);
        if (!activo) {
            t.setFechaFin(LocalDateTime.now().minusHours(1));
        }
        return em.persistAndFlush(t);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void findByActivo_returnsEmpty_whenNoActiveTurno() {
        // Given: persist only a closed turno
        Usuario usuario = persistUsuario("user_closed");
        persistTurno(usuario, false);
        em.clear();

        // When
        Optional<Turno> result = turnoRepository.findByActivo(true);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findByActivo_returnsActiveTurno_whenOneExists() {
        // Given: one active + one closed turno
        Usuario usuario = persistUsuario("user_active");
        Turno activeTurno = persistTurno(usuario, true);
        em.clear();

        // When
        Optional<Turno> result = turnoRepository.findByActivo(true);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(activeTurno.getId());
        assertThat(result.get().getActivo()).isTrue();
    }

    @Test
    void findByUsuarioIdAndActivo_filtersByUsuarioAndState() {
        // Given: two users, each with one active turno
        Usuario usuario1 = persistUsuario("user_uno");
        Usuario usuario2 = persistUsuario("user_dos");
        Turno turno1 = persistTurno(usuario1, true);
        persistTurno(usuario2, false);
        em.clear();

        // When: query by usuario1 + activo=true
        Optional<Turno> result = turnoRepository.findByUsuarioIdAndActivo(usuario1.getId(), true);

        // Then: only turno1 is returned
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(turno1.getId());

        // And: usuario2 active query returns empty (turno2 was closed)
        Optional<Turno> noResult = turnoRepository.findByUsuarioIdAndActivo(usuario2.getId(), true);
        assertThat(noResult).isEmpty();
    }
}
