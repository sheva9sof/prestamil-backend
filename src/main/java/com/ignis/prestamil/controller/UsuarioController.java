package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.request.CambiarPasswordRequest;
import com.ignis.prestamil.response.UsuarioResponse;
import com.ignis.prestamil.service.UsuarioService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    
    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;

    public UsuarioController(UsuarioService usuarioService, UsuarioMapper usuarioMapper) {
        this.usuarioService = usuarioService;
        this.usuarioMapper = usuarioMapper;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> findAll() {
        List<Usuario> usuarios = usuarioService.findAll();
        List<UsuarioResponse> responses = usuarios.stream()
                .map(usuarioMapper::toUsuarioResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/page")
    public ResponseEntity<Page<UsuarioResponse>> findAll(Pageable pageable) {
        Page<Usuario> usuarios = usuarioService.findAll(pageable);
        Page<UsuarioResponse> responses = usuarios.map(usuarioMapper::toUsuarioResponse);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> findById(@PathVariable Integer id) {
        Optional<Usuario> usuario = usuarioService.findById(id);
        return usuario.map(u -> ResponseEntity.ok(usuarioMapper.toUsuarioResponse(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> create(@RequestBody Usuario usuario) {
        // Encriptar password si se proporciona
        if (usuario.getPassword() != null && !usuario.getPassword().isEmpty()) {
            String passwordEncriptada = usuarioService.encryptPassword(usuario.getPassword());
            usuario.setPassword(passwordEncriptada);
        }
        
        Usuario savedUsuario = usuarioService.save(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toUsuarioResponse(savedUsuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> update(@PathVariable Integer id, @RequestBody Usuario usuario) {
        if (!usuarioService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        usuario.setId(id);
        
        // Si se proporciona un nuevo password, encriptarlo
        if (usuario.getPassword() != null && !usuario.getPassword().isEmpty()) {
            String passwordEncriptada = usuarioService.encryptPassword(usuario.getPassword());
            usuario.setPassword(passwordEncriptada);
        } else {
            // Mantener el password existente si no se proporciona uno nuevo
            Usuario usuarioExistente = usuarioService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            usuario.setPassword(usuarioExistente.getPassword());
        }
        
        Usuario updatedUsuario = usuarioService.update(usuario);
        return ResponseEntity.ok(usuarioMapper.toUsuarioResponse(updatedUsuario));
    }

    @PutMapping("/{id}/cambiar-password")
    public ResponseEntity<Map<String, String>> cambiarPassword(
            @PathVariable Integer id,
            @RequestBody CambiarPasswordRequest request) {
        
        if (!usuarioService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            usuarioService.cambiarPassword(id, request.getPasswordActual(), request.getPasswordNueva());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Contraseña cambiada exitosamente");
            response.put("status", "success");
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("status", "error");
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

}
