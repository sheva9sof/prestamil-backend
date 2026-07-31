package com.ignis.prestamil.controller;

import com.ignis.prestamil.request.LoginRequest;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Inyectado desde SecurityConfig. Se usa para persistir el SecurityContext
     * en la sesión JDBC de forma explícita (obligatorio en Spring Security 6).
     */
    @Autowired
    private SecurityContextRepository securityContextRepository;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired(required = false)
    private LogoutStreamController logoutStreamController;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        if (logoutStreamController != null) {
            logoutStreamController.sendForceLogoutEvent(loginRequest.getUsername());
        }

        // 1. Validar credenciales mediante la lógica de negocio (lanza excepción si falla)
        LoginResponse loginResponse = usuarioService.login(loginRequest);
        loginResponse.setPassword(null);
        // 2. Construir el token de autenticación con rol genérico
        //    (la lógica de roles detallada vive en la DB; ROLE_USER permite acceso a /api/**)
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                loginResponse.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // Renueva el id de sesión para evitar fixation y marca esta sesión como la vigente.
        request.getSession(true);
        request.changeSessionId();
        String currentSessionId = request.getSession(false).getId();

        // Fuerza sesión única: elimina cualquier sesión previa del mismo usuario.
        invalidatePreviousSessions(loginResponse.getUsername(), currentSessionId);

        // 3. Crear un SecurityContext limpio y asignarle la autenticación
        SecurityContext newContext = SecurityContextHolder.createEmptyContext();
        newContext.setAuthentication(auth);
        SecurityContextHolder.setContext(newContext);

        // 4. Persistir el SecurityContext en la sesión HTTP (JSESSIONID)
        //    ⚠ Spring Security 6 NO guarda el contexto automáticamente.
        //    Sin esta línea el JSESSIONID se genera pero queda vacío → 403 en
        //    todas las peticiones siguientes aunque la cookie sea enviada.
        securityContextRepository.saveContext(newContext, request, response);

        return ResponseEntity.ok(loginResponse);
    }

    private void invalidatePreviousSessions(String username, String currentSessionId) {
        Map<String, ? extends Session> activeSessions = sessionRepository.findByPrincipalName(username);
        activeSessions.forEach((sessionId, session) -> {
            if (!sessionId.equals(currentSessionId)) {
                sessionRepository.deleteById(sessionId);
            }
        });
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

    @GetMapping("/keep-alive")
    public ResponseEntity<Void> keepAlive() {
        return ResponseEntity.ok().build();
    }
}
