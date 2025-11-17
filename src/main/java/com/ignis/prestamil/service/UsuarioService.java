package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.LoginRequest;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.util.Encryptor;
import com.ignis.prestamil.util.JwtUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UsuarioService extends BaseService<Usuario, Integer, UsuarioRepository> {

    private final Encryptor encryptor;
    private final UsuarioMapper usuarioMapper;
    private final JwtUtil jwtUtil;

    public UsuarioService(UsuarioRepository repository, Encryptor encryptor, UsuarioMapper usuarioMapper, JwtUtil jwtUtil) {
        super(repository);
        this.encryptor = encryptor;
        this.usuarioMapper = usuarioMapper;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        Usuario usuario = repository.findByNombreUsuario(loginRequest.getNombreUsuario())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        String passwordDesencriptada = encryptor.decrypt(usuario.getPassword());
        
        if (passwordDesencriptada == null || !passwordDesencriptada.equals(loginRequest.getPassword())) {
            throw new RuntimeException("Password incorrecta");
        }
        
        // Actualizar campos de sesión
        LocalDateTime now = LocalDateTime.now();
        usuario.setUltimoLogin(now);
        usuario.setInicioSesion(now);
        usuario.setUltimaActividad(now);
        repository.save(usuario);

        // Obtener las opciones del usuario
        List<Opcion> opciones = getOpcionesByUsuario(usuario.getNombreUsuario());
      
        // Generar token JWT
        String token = jwtUtil.generateToken(
            usuario.getNombreUsuario(),
            usuario.getId(),
            usuario.getRol() != null ? usuario.getRol().getId() : null
        );
      
        // Mapear Usuario a LoginResponse usando el mapper, incluyendo las opciones y el token
        LoginResponse response = usuarioMapper.toLoginResponse(usuario, opciones);
        response.setToken(token);
        return response;
    }

    /**
     * Obtiene las opciones del usuario basándose en su rol
     * @param nombreUsuario Nombre de usuario
     * @return Lista de Opciones
     */
    public List<Opcion> getOpcionesByUsuario(String nombreUsuario) {
        Usuario usuario = repository.findWithRolAndOpcionesByNombreUsuario(nombreUsuario)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        if (usuario.getRol() == null) {
            throw new RuntimeException("El usuario no tiene un rol asignado");
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

}
