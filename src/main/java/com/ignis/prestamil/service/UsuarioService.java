package com.ignis.prestamil.service;

import com.ignis.prestamil.request.LoginRequest;
import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.RolRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.response.TurnoResponse;
import com.ignis.prestamil.util.Constantes;
import com.ignis.prestamil.util.Encryptor;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class UsuarioService extends BaseService<Usuario, Integer, UsuarioRepository> {

    private final Encryptor encryptor;
    private final UsuarioMapper usuarioMapper;
    private final RolRepository rolRepository;
    private final TurnoService turnoService;
    private final ConfiguracionService configuracionService;

    public UsuarioService(UsuarioRepository repository, Encryptor encryptor, UsuarioMapper usuarioMapper, RolRepository rolRepository,
                          TurnoService turnoService, ConfiguracionService configuracionService) {
        super(repository);
        this.encryptor = encryptor;
        this.usuarioMapper = usuarioMapper;
        this.rolRepository = rolRepository;
        this.turnoService = turnoService;
        this.configuracionService = configuracionService;
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
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        usuario.setEstatus(false);
        repository.save(usuario);
    }

    public LoginResponse login(LoginRequest loginRequest) {
        Usuario usuario = repository.findByNombreUsuario(loginRequest.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        String passwordDesencriptada = encryptor.decrypt(usuario.getPassword());
        
        if (passwordDesencriptada == null || !passwordDesencriptada.equals(loginRequest.getPassword())) {
            throw new BadRequestException("Password incorrecta");
        }
        
        // Actualizar campos de sesión
        LocalDateTime now = LocalDateTime.now();
        usuario.setUltimoLogin(now);
        usuario.setInicioSesion(now);
        usuario.setUltimaActividad(now);
        repository.save(usuario);

        // --- INICIO CAMBIO: Registrar sesión en Spring Security ---
        // Creamos la autoridad basada en el rol (si existe)
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (usuario.getRol() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getId())); // O usa el nombre del rol
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(usuario.getNombreUsuario(), null, authorities);
        
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Guardar explícitamente el contexto en la sesión HTTP (Requerido en Spring Boot 3)
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpSession session = attr.getRequest().getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        }
        // --- FIN CAMBIO ---

        // Obtener las opciones del usuario y filtrarlas según el estado del turno
        List<Opcion> opciones = getOpcionesByUsuario(usuario.getNombreUsuario());
        opciones = filtrarOpcionesPorTurno(opciones, usuario);

        // Mapear Usuario a LoginResponse usando el mapper, incluyendo las opciones y el token
        LoginResponse response = usuarioMapper.toLoginResponse(usuario, opciones);
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
        return usuarioMapper.toLoginResponse(usuario, opciones);
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

    /**
     * Encripta un password
     * @param password Password en texto plano
     * @return Password encriptado
     */
    public String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        return encryptor.encrypt(password);
    }

    /**
     * Cambia la contraseña de un usuario
     * @param usuarioId ID del usuario
     * @param passwordActual Contraseña actual en texto plano
     * @param passwordNueva Nueva contraseña en texto plano
     */
    public void cambiarPassword(Integer usuarioId, String passwordActual, String passwordNueva) {
        Usuario usuario = repository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Validar contraseña actual
        String passwordDesencriptada = encryptor.decrypt(usuario.getPassword());
        if (passwordDesencriptada == null || !passwordDesencriptada.equals(passwordActual)) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        // Validar que la nueva contraseña sea diferente a la actual
        if (passwordDesencriptada.equals(passwordNueva)) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la contraseña actual");
        }

        // Validar que la nueva contraseña no esté vacía
        if (passwordNueva == null || passwordNueva.isEmpty()) {
            throw new BadRequestException("La nueva contraseña no puede estar vacía");
        }

        // Encriptar y actualizar la nueva contraseña
        String passwordNuevaEncriptada = encryptor.encrypt(passwordNueva);
        usuario.setPassword(passwordNuevaEncriptada);
        usuario.setCambiarPassword(false);
        usuario.setFechaCambioPass(java.time.LocalDate.now());
        
        repository.save(usuario);
    }

    public List<Usuario> findAll(Specification<Usuario> spec) {
        return repository.findAll(spec);
    }
}
