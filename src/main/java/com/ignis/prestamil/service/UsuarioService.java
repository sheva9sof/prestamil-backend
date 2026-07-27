package com.ignis.prestamil.service;

import com.ignis.prestamil.request.LoginRequest;
import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.repository.RolRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.response.MenuResponse;
import com.ignis.prestamil.response.TurnoResponse;
import com.ignis.prestamil.util.Constantes;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class UsuarioService extends BaseService<Usuario, Integer, UsuarioRepository> {

    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final RolRepository rolRepository;
    private final TurnoService turnoService;
    private final ConfiguracionService configuracionService;
    private final ParametrosSistemaRepository parametrosSistemaRepository;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, UsuarioMapper usuarioMapper, RolRepository rolRepository,
                          TurnoService turnoService, ConfiguracionService configuracionService,
                          ParametrosSistemaRepository parametrosSistemaRepository) {
        super(repository);
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
        this.rolRepository = rolRepository;
        this.turnoService = turnoService;
        this.configuracionService = configuracionService;
        this.parametrosSistemaRepository = parametrosSistemaRepository;
    }

    @Override
    public Usuario save(Usuario usuario) {
        // Manejar la asignación del Rol
        if (usuario.getRol() != null && usuario.getRol().getId() != null) {
            Rol rol = rolRepository.findById(usuario.getRol().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con id: " + usuario.getRol().getId()));
            usuario.setRol(rol);
        }

        // Encriptar password si se proporciona
        if (usuario.getPassword() != null && !usuario.getPassword().isEmpty()) {
            usuario.setPassword(encryptPassword(usuario.getPassword()));
        }
        // Asignar valores por defecto para campos no nulos
        usuario.setCreado(LocalDateTime.now());
        usuario.setFechaCambioPass(LocalDate.now());
        
        return super.save(usuario);
    }

    public Usuario update(Integer id, Usuario usuarioDetails) {
        // 1. Obtener el usuario existente de la base de datos
        Usuario usuarioExistente = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));

        // 2. Actualizar los campos del usuario existente solo si se proporcionan en la petición
        if (usuarioDetails.getNombreUsuario() != null) {
            usuarioExistente.setNombreUsuario(usuarioDetails.getNombreUsuario());
        }
        if (usuarioDetails.getNombre() != null) {
            usuarioExistente.setNombre(usuarioDetails.getNombre());
        }
        if (usuarioDetails.getApellidos() != null) {
            usuarioExistente.setApellidos(usuarioDetails.getApellidos());
        }
        // Manejar la actualización del Rol
        if (usuarioDetails.getRol() != null && usuarioDetails.getRol().getId() != null) {
            Rol rol = rolRepository.findById(usuarioDetails.getRol().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con id: " + usuarioDetails.getRol().getId()));
            usuarioExistente.setRol(rol);
        }
        if (usuarioDetails.getFechaIni() != null) {
            usuarioExistente.setFechaIni(usuarioDetails.getFechaIni());
        }
        if (usuarioDetails.getFechaFin() != null) {
            usuarioExistente.setFechaFin(usuarioDetails.getFechaFin());
        }
        if (usuarioDetails.getFechaCambioPass() != null) {
            usuarioExistente.setFechaCambioPass(usuarioDetails.getFechaCambioPass());
        }

        // Los campos booleanos se actualizarán con el valor que llegue (que será 'false' si no se especifica)
        usuarioExistente.setEstatus(usuarioDetails.isEstatus());
        usuarioExistente.setCambiarPassword(usuarioDetails.isCambiarPassword());
        usuarioExistente.setVigencia(usuarioDetails.isVigencia());
        usuarioExistente.setAplicaCambioPassword(usuarioDetails.isAplicaCambioPassword());
        usuarioExistente.setEditable(usuarioDetails.isEditable());
        
        usuarioExistente.setUpdatedAt(LocalDateTime.now());

        // 3. Manejar la actualización de la contraseña de forma segura
        if (usuarioDetails.getPassword() != null && !usuarioDetails.getPassword().isEmpty()) {
            usuarioExistente.setPassword(encryptPassword(usuarioDetails.getPassword()));
        }

        // 4. Guardar la entidad actualizada
        return repository.save(usuarioExistente);
    }

    @Override
    public void deleteById(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + id);
        }
        // Eliminación física. El estatus se administra por separado desde la edición.
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException(
                    "No se puede eliminar este usuario porque tiene turnos, contratos o movimientos relacionados.");
        }
    }

    public LoginResponse login(LoginRequest loginRequest) {
        Usuario usuario = repository.findByNombreUsuario(loginRequest.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        if (!passwordEncoder.matches(loginRequest.getPassword(), usuario.getPassword())) {
            throw new BadRequestException("Password incorrecta");
        }
        
        // Actualizar campos de sesión
        LocalDateTime now = LocalDateTime.now();
        usuario.setUltimoLogin(now);
        usuario.setInicioSesion(now);
        usuario.setUltimaActividad(now);
        repository.save(usuario);


        // Obtener las opciones del usuario y filtrarlas según el estado del turno
        List<Opcion> opciones = getOpcionesByUsuario(usuario.getNombreUsuario());
        opciones = filtrarOpcionesPorTurno(opciones, usuario);

        // Mapear Usuario a LoginResponse usando el mapper, incluyendo las opciones y el token
        LoginResponse response = usuarioMapper.toLoginResponse(usuario, opciones);
        response.setOpciones(construirMenuJerarquico(opciones));

        int sessionTimeoutMinutes = parametrosSistemaRepository.findById(16)
            .map(p -> p.getValorNumerico() != null ? p.getValorNumerico().intValue() : 30)
            .orElse(30);
        int warningMinutes = parametrosSistemaRepository.findById(17)
            .map(p -> p.getValorNumerico() != null ? p.getValorNumerico().intValue() : 3)
            .orElse(3);
        response.setSessionTimeoutMinutes(sessionTimeoutMinutes);
        response.setWarningMinutes(warningMinutes);

        return response;
    }

    public LoginResponse obtenerMiPerfil(String nombreUsuario) {
        Usuario usuario = repository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        // Obtener las opciones del usuario y filtrarlas según el estado del turno
        List<Opcion> opciones = getOpcionesByUsuario(nombreUsuario);
        try {
            opciones = filtrarOpcionesPorTurno(opciones, usuario);
        } catch (ResourceNotFoundException exception) {
            opciones = new ArrayList<>();
        }

        // Reutilizamos el LoginResponse para devolver el perfil. El token será null, lo cual está bien.
        LoginResponse response = usuarioMapper.toLoginResponse(usuario, opciones);
        response.setOpciones(construirMenuJerarquico(opciones));
        return response;
    }

    private List<MenuResponse> construirMenuJerarquico(List<Opcion> opciones) {
        if (opciones == null || opciones.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, MenuResponse> menuPorId = new LinkedHashMap<>();
        for (Opcion opcion : opciones) {
            if (opcion.getId() != null) {
                menuPorId.put(opcion.getId(), usuarioMapper.toMenuResponse(opcion));
            }
        }

        List<MenuResponse> menusRaiz = new ArrayList<>();
        for (Opcion opcion : opciones) {
            if (opcion.getId() == null) {
                continue;
            }

            MenuResponse menuActual = menuPorId.get(opcion.getId());
            Integer idPadre = opcion.getIdPadre();

            if (idPadre != null && menuPorId.containsKey(idPadre) && !idPadre.equals(opcion.getId())) {
                menuPorId.get(idPadre).getSubmenus().add(menuActual);
            } else {
                menusRaiz.add(menuActual);
            }
        }

        return menusRaiz;
    }

    private List<Opcion> filtrarOpcionesPorTurno(List<Opcion> opcionesOriginales, Usuario usuario) {
        TurnoResponse turnoActivo = turnoService.obtenerTurnoActivo();

        if (turnoActivo == null) {
            // No hay turno activo, verificar si el usuario tiene permiso para abrir uno
            Optional<Configuracion> configRolesOpt = configuracionService.findByConfiguracion(Constantes.ROLES_PERMITIDOS_APERTURA_TURNOS);
            
            if (configRolesOpt.isPresent() && usuario.getRol() != null) {
                String rolesPermitidosStr = configRolesOpt.get().getValorCadena();
                List<Integer> rolesPermitidos = Arrays.stream(rolesPermitidosStr.split(","))
                                                    .map(String::trim)
                                                    .map(Integer::parseInt)
                                                    .toList();
                
                if (rolesPermitidos.contains(usuario.getRol().getId())) {
                    // El usuario tiene un rol permitido, mostrar solo el menú "Turno"
                    return opcionesOriginales.stream()
                                    .filter(opcion -> Constantes.NOMBRE_MENU_TURNO.equals(opcion.getOpcion()))
                                    .toList();
                } else {
                    // El usuario no tiene un rol permitido, no puede hacer nada
                    throw new ResourceNotFoundException("No hay un turno activo y no tiene permiso para abrir uno.");
                }
            } else {
                // Si no se encuentra el parámetro o el usuario no tiene rol, no puede hacer nada
                throw new ResourceNotFoundException("No hay un turno activo en el sistema.");
            }
        }
        // Si hay un turno activo, devolver la lista de opciones original sin cambios
        return opcionesOriginales;
    }

    /**
     * Obtiene las opciones del usuario basándose en su rol
     * @param nombreUsuario Nombre de usuario
     * @return Lista de Opciones
     */
    public List<Opcion> getOpcionesByUsuario(String nombreUsuario) {
        Usuario usuario = repository.findWithRolAndOpcionesByNombreUsuario(nombreUsuario)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        if (usuario.getRol() == null) {
            throw new BadRequestException("El usuario no tiene un rol asignado");
        }
        
        // Usar el método helper getOpciones() del Rol
        return usuario.getRol().getOpciones();
    }

    public String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        return passwordEncoder.encode(password);
    }

    public void cambiarPassword(Integer usuarioId, String passwordActual, String passwordNueva) {
        Usuario usuario = repository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        if (passwordNueva == null || passwordNueva.isEmpty()) {
            throw new BadRequestException("La nueva contraseña no puede estar vacía");
        }

        if (passwordEncoder.matches(passwordNueva, usuario.getPassword())) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la contraseña actual");
        }

        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuario.setCambiarPassword(false);
        usuario.setFechaCambioPass(java.time.LocalDate.now());
        repository.save(usuario);
    }

    public List<Usuario> findAll(Specification<Usuario> spec) {
        return repository.findAll(spec);
    }
}
