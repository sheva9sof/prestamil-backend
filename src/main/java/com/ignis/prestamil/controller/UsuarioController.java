package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.UsuarioMapper;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.request.CambiarPasswordRequest;
import com.ignis.prestamil.response.UsuarioResponse;
import com.ignis.prestamil.service.UsuarioService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public List<UsuarioResponse> findAll() {
        List<Usuario> usuarios = usuarioService.findAll();
        return usuarios.stream()
                .map(usuarioMapper::toUsuarioResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/page")
    public Page<UsuarioResponse> findAll(Pageable pageable) {
        Page<Usuario> usuarios = usuarioService.findAll(pageable);
        return usuarios.map(usuarioMapper::toUsuarioResponse);
    }

    @GetMapping("/{id}")
    public UsuarioResponse findById(@PathVariable Integer id) {
        Usuario usuario = usuarioService.findById(id);
        return usuarioMapper.toUsuarioResponse(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse create(@RequestBody Usuario usuario) {
        Usuario savedUsuario = usuarioService.save(usuario);
        return usuarioMapper.toUsuarioResponse(savedUsuario);
    }

    @PutMapping("/{id}")
    public UsuarioResponse update(@PathVariable Integer id, @RequestBody Usuario usuarioDetails) {
        Usuario updatedUsuario = usuarioService.update(id, usuarioDetails);
        return usuarioMapper.toUsuarioResponse(updatedUsuario);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        usuarioService.deleteById(id);
    }

    @GetMapping("/buscar")
    public List<UsuarioResponse> search(@RequestParam(required = false) String nombre,
                                        @RequestParam(required = false) String nombreUsuario,
                                        @RequestParam(required = false) Boolean estatus) {
        
        // Forma moderna de iniciar una Specification
        Specification<Usuario> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (nombre != null && !nombre.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%"));
        }

        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("nombreUsuario"), nombreUsuario));
        }

        if (estatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estatus"), estatus));
        }

        List<Usuario> usuarios = usuarioService.findAll(spec);
        return usuarios.stream()
                .map(usuarioMapper::toUsuarioResponse)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/cambiar-password")
    public Map<String, String> cambiarPassword(
            @PathVariable Integer id,
            @RequestBody CambiarPasswordRequest request) {
        
        usuarioService.cambiarPassword(id, request.getPasswordActual(), request.getPasswordNueva());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Contraseña cambiada exitosamente");
        response.put("status", "success");
        
        return response;
    }

}
