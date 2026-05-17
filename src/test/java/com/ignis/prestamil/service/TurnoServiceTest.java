package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.exception.ValidationException;
import com.ignis.prestamil.mapper.TurnoMapper;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.TurnoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock
    TurnoRepository turnoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    TurnoMapper turnoMapper;

    @InjectMocks
    TurnoService turnoService;

    private Usuario usuarioAdmin;
    private Turno turnoActivo;

    @BeforeEach
    void setUp() {
        usuarioAdmin = new Usuario();
        usuarioAdmin.setNombreUsuario("admin");
        usuarioAdmin.setNombre("Admin");
        usuarioAdmin.setApellidos("Test");
        usuarioAdmin.setPassword("$2a$10$hash");
        usuarioAdmin.setEstatus(true);
        usuarioAdmin.setCreado(LocalDateTime.now());

        turnoActivo = new Turno();
        turnoActivo.setId(1);
        turnoActivo.setUsuario(usuarioAdmin);
        turnoActivo.setFechaInicio(LocalDateTime.now().minusHours(1));
        turnoActivo.setActivo(true);
    }

    /** Helper: sets up SecurityContext to return the given username. */
    private void mockSecurityContext(String nombreUsuario) {
        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(nombreUsuario);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // iniciarTurno tests
    // -----------------------------------------------------------------------

    @Test
    void iniciarTurno_whenNoActiveTurno_createsAndReturnsNewTurno() {
        // Given
        mockSecurityContext("admin");
        when(usuarioRepository.findByNombreUsuario("admin")).thenReturn(Optional.of(usuarioAdmin));
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.empty());
        Turno savedTurno = new Turno();
        savedTurno.setId(1);
        savedTurno.setUsuario(usuarioAdmin);
        savedTurno.setFechaInicio(LocalDateTime.now());
        savedTurno.setActivo(true);
        when(turnoRepository.save(any(Turno.class))).thenReturn(savedTurno);
        TurnoResponse expectedResponse = new TurnoResponse();
        expectedResponse.setId(1);
        when(turnoMapper.toTurnoResponse(savedTurno)).thenReturn(expectedResponse);

        // When
        TurnoResponse result = turnoService.iniciarTurno();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1);

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoRepository).save(captor.capture());
        Turno capturedTurno = captor.getValue();
        assertThat(capturedTurno.getActivo()).isTrue();
        assertThat(capturedTurno.getFechaInicio()).isNotNull();
        assertThat(capturedTurno.getUsuario()).isEqualTo(usuarioAdmin);
    }

    @Test
    void iniciarTurno_whenActiveTurnoExists_throwsValidationException() {
        // Given
        mockSecurityContext("admin");
        when(usuarioRepository.findByNombreUsuario("admin")).thenReturn(Optional.of(usuarioAdmin));
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turnoActivo));

        // When / Then
        assertThrows(ValidationException.class, () -> turnoService.iniciarTurno());
        verify(turnoRepository, never()).save(any());
    }

    @Test
    void iniciarTurno_whenUsuarioNotFound_throwsResourceNotFoundException() {
        // Given — auth returns "ghost" user not in DB
        mockSecurityContext("ghost");
        when(usuarioRepository.findByNombreUsuario("ghost")).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ResourceNotFoundException.class, () -> turnoService.iniciarTurno());
    }

    // -----------------------------------------------------------------------
    // cerrarTurno tests
    // -----------------------------------------------------------------------

    @Test
    void cerrarTurno_whenActive_closesAndReturnsResponse() {
        // Given
        when(turnoRepository.findById(1)).thenReturn(Optional.of(turnoActivo));
        Turno closedTurno = new Turno();
        closedTurno.setId(1);
        closedTurno.setActivo(false);
        closedTurno.setFechaFin(LocalDateTime.now());
        when(turnoRepository.save(any(Turno.class))).thenReturn(closedTurno);
        TurnoResponse expectedResponse = new TurnoResponse();
        expectedResponse.setId(1);
        expectedResponse.setActivo(false);
        when(turnoMapper.toTurnoResponse(any(Turno.class))).thenReturn(expectedResponse);

        // When
        TurnoResponse result = turnoService.cerrarTurno(1);

        // Then
        assertThat(result).isNotNull();
        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoRepository).save(captor.capture());
        Turno capturedTurno = captor.getValue();
        assertThat(capturedTurno.getActivo()).isFalse();
        assertThat(capturedTurno.getFechaFin()).isNotNull();
    }

    @Test
    void cerrarTurno_whenAlreadyClosed_throwsValidationException() {
        // Given
        Turno turnoCerrado = new Turno();
        turnoCerrado.setId(2);
        turnoCerrado.setActivo(false);
        turnoCerrado.setFechaFin(LocalDateTime.now().minusMinutes(30));
        when(turnoRepository.findById(2)).thenReturn(Optional.of(turnoCerrado));

        // When / Then
        assertThrows(ValidationException.class, () -> turnoService.cerrarTurno(2));
        verify(turnoRepository, never()).save(any());
    }

    @Test
    void cerrarTurno_whenNotFound_throwsResourceNotFoundException() {
        // Given
        when(turnoRepository.findById(99)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ResourceNotFoundException.class, () -> turnoService.cerrarTurno(99));
    }

    // -----------------------------------------------------------------------
    // obtenerTurnoActivo tests
    // -----------------------------------------------------------------------

    @Test
    void obtenerTurnoActivo_whenExists_returnsMappedResponse() {
        // Given
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turnoActivo));
        TurnoResponse expectedResponse = new TurnoResponse();
        expectedResponse.setId(1);
        expectedResponse.setActivo(true);
        when(turnoMapper.toTurnoResponse(turnoActivo)).thenReturn(expectedResponse);

        // When
        TurnoResponse result = turnoService.obtenerTurnoActivo();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getActivo()).isTrue();
    }

    @Test
    void obtenerTurnoActivo_whenNone_returnsNull() {
        // Given
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.empty());

        // When
        TurnoResponse result = turnoService.obtenerTurnoActivo();

        // Then
        assertThat(result).isNull();
    }
}
