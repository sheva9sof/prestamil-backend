package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.LoginRequest;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired(required = false)
    private LogoutStreamController logoutStreamController;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (logoutStreamController != null) {
             logoutStreamController.sendForceLogoutEvent(loginRequest.getUsername());
        }

        // 1. Usar la lógica personalizada de UsuarioService (valida pass, actualiza fechas, etc.)
        LoginResponse response = usuarioService.login(loginRequest);

        // 2. Establecer manualmente la autenticación en el contexto de Spring Security
        // Nota: Asignamos un rol genérico por ahora ya que la lógica de roles es personalizada en tu DB
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                response.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        // 3. Crear la sesión HTTP
        request.getSession(true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logout successful");
    }
}
