package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.RolOpcion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.repository.RolRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository repository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    UsuarioMapper usuarioMapper;

    @Mock
    RolRepository rolRepository;

    @Mock
    TurnoService turnoService;

    @Mock
    ConfiguracionService configuracionService;

    @Mock
    ParametrosSistemaRepository parametrosSistemaRepository;

    UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                repository,
                passwordEncoder,
                usuarioMapper,
                rolRepository,
                turnoService,
                configuracionService,
                parametrosSistemaRepository
        );
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Usuario buildUsuario(String nombreUsuario, String password) {
        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario);
        u.setNombre("Test");
        u.setApellidos("User");
        u.setPassword(password);
        u.setEstatus(true);
        u.setCreado(LocalDateTime.now());
        return u;
    }

    private Rol buildRol(Integer id) {
        Rol rol = new Rol();
        rol.setId(id);
        rol.setRol("ADMIN");
        rol.setEstatus(true);
        return rol;
    }

    // -----------------------------------------------------------------------
    // save tests
    // -----------------------------------------------------------------------

    @Test
    void save_encryptsPasswordBeforePersisting() {
        // Given
        Rol rol = buildRol(1);
        Usuario usuario = buildUsuario("jdoe", "Secret123");
        usuario.setRol(rol);

        when(rolRepository.findById(1)).thenReturn(Optional.of(rol));
        when(passwordEncoder.encode("Secret123")).thenReturn("BCRYPT_HASH");
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Usuario result = usuarioService.save(usuario);

        // Then
        verify(passwordEncoder).encode("Secret123");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        Usuario captured = captor.getValue();
        assertThat(captured.getPassword()).isEqualTo("BCRYPT_HASH");
        assertThat(captured.getCreado()).isNotNull();
        assertThat(captured.getFechaCambioPass()).isNotNull();
    }

    @Test
    void save_whenPasswordEmpty_doesNotCallEncoder() {
        // Given — usuario with empty password and no rol to avoid rolRepository lookup
        Usuario usuario = buildUsuario("jdoe", "");
        usuario.setRol(null);
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        usuarioService.save(usuario);

        // Then
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void save_whenRolIdNotFound_throwsResourceNotFoundException() {
        // Given
        Rol rolRef = buildRol(999);
        Usuario usuario = buildUsuario("jdoe", "pass");
        usuario.setRol(rolRef);

        when(rolRepository.findById(999)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ResourceNotFoundException.class, () -> usuarioService.save(usuario));
        verify(repository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // deleteById tests
    // -----------------------------------------------------------------------

    @Test
    void deleteById_existingUser_performsHardDelete() {
        // Given
        when(repository.existsById(1)).thenReturn(true);

        // When
        usuarioService.deleteById(1);

        // Then
        verify(repository).deleteById(1);
        verify(repository).flush();
        verify(repository, never()).save(any());
    }

    @Test
    void deleteById_whenNotFound_throwsResourceNotFoundException() {
        // Given
        when(repository.existsById(999)).thenReturn(false);

        // When / Then
        assertThrows(ResourceNotFoundException.class, () -> usuarioService.deleteById(999));
        verify(repository, never()).deleteById(any());
        verify(repository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // encryptPassword tests
    // -----------------------------------------------------------------------

    @Test
    void encryptPassword_whenNull_returnsNull() {
        // When
        String result = usuarioService.encryptPassword(null);

        // Then
        assertThat(result).isNull();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void encryptPassword_whenValid_delegatesToEncoder() {
        // Given
        when(passwordEncoder.encode("MyPassword")).thenReturn("BCRYPT_RESULT");

        // When
        String result = usuarioService.encryptPassword("MyPassword");

        // Then
        assertThat(result).isEqualTo("BCRYPT_RESULT");
        verify(passwordEncoder).encode("MyPassword");
    }

    // -----------------------------------------------------------------------
    // Menú sin turno abierto (CAJ-06)
    // -----------------------------------------------------------------------

    @Test
    void sinTurno_gerenteVeTurnosYElCorteDeCaja() {
        Usuario gerente = usuarioConMenus("gerente", 5, "Turnos", "Caja", "Corte de caja", "Dotación", "Avaluos");

        assertThat(menuSinTurno(gerente)).containsExactly("Turnos", "Caja", "Corte de caja");
    }

    @Test
    void sinTurno_sistemasEntraConElCorteYElArranqueParaCerrarUnDiaPendiente() {
        // Sistemas no abre turno (ROLES_PERMITIDOS_APERTURA_TURNOS = 5), pero cierra los días pendientes
        Usuario sistemas = usuarioConMenus("sistemas", 1, "Caja", "Corte de caja", "Arranque de caja", "Usuarios");

        assertThat(menuSinTurno(sistemas)).containsExactly("Caja", "Corte de caja", "Arranque de caja");
    }

    @Test
    void sinTurno_elCajeroSigueSinMenu() {
        Usuario cajero = usuarioConMenus("cajero", 3, "Caja", "Corte de caja", "Dotación");

        assertThat(menuSinTurno(cajero)).isEmpty();
    }

    /** Nombres de los menús del perfil sin turno activo, con la configuración de 086. */
    private List<String> menuSinTurno(Usuario usuario) {
        when(repository.findByNombreUsuario(usuario.getNombreUsuario())).thenReturn(Optional.of(usuario));
        when(repository.findWithRolAndOpcionesByNombreUsuario(usuario.getNombreUsuario())).thenReturn(Optional.of(usuario));
        when(turnoService.obtenerTurnoActivo()).thenReturn(null);
        lenient().when(configuracionService.findByConfiguracion(Constantes.ROLES_PERMITIDOS_APERTURA_TURNOS))
                .thenReturn(Optional.of(configuracion("5")));
        lenient().when(configuracionService.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA))
                .thenReturn(Optional.of(configuracion("5,1")));
        lenient().when(configuracionService.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE))
                .thenReturn(Optional.of(configuracion("1")));
        when(usuarioMapper.toLoginResponse(any(), any())).thenReturn(new LoginResponse());

        usuarioService.obtenerMiPerfil(usuario.getNombreUsuario());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Opcion>> opciones = ArgumentCaptor.forClass(List.class);
        verify(usuarioMapper).toLoginResponse(eq(usuario), opciones.capture());
        return opciones.getValue().stream().map(Opcion::getOpcion).toList();
    }

    private Usuario usuarioConMenus(String nombreUsuario, int rolId, String... menus) {
        Rol rol = buildRol(rolId);
        int id = 1;
        for (String menu : menus) {
            Opcion opcion = new Opcion();
            opcion.setId(id++);
            opcion.setOpcion(menu);
            RolOpcion rolOpcion = new RolOpcion();
            rolOpcion.setRol(rol);
            rolOpcion.setOpcion(opcion);
            rol.getRolOpciones().add(rolOpcion);
        }
        Usuario usuario = buildUsuario(nombreUsuario, "x");
        usuario.setRol(rol);
        return usuario;
    }

    private static Configuracion configuracion(String csv) {
        Configuracion c = new Configuracion();
        c.setValorCadena(csv);
        return c;
    }
}
