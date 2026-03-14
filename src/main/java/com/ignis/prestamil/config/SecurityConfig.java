package com.ignis.prestamil.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionRegistry sessionRegistry;

    public SecurityConfig(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Repositorio que persiste el SecurityContext en la HttpSession (JSESSIONID).
     * Se expone como @Bean para poder inyectarlo en AuthController y guardar
     * el contexto explícitamente tras el login (requisito de Spring Security 6).
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CORS: usa el bean CorsConfigurationSource de CorsConfig ──────────
            .cors(Customizer.withDefaults())

            // ── CSRF deshabilitado (API REST + sesión con CORS controlado) ────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Contexto de seguridad: guarda explícitamente en la sesión ─────────
            // Spring Security 6 NO guarda el SecurityContext automáticamente.
            // requireExplicitSave(true) es el default; AuthController llama
            // securityContextRepository.saveContext() tras el login.
            .securityContext(sc -> sc
                .securityContextRepository(securityContextRepository())
                .requireExplicitSave(true)
            )

            // ── Reglas de autorización ────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS nunca debe bloquearse
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Endpoints públicos de autenticación
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers("/auth/**", "/api/auth/**", "/auth/stream/**").permitAll()
                // Todo lo demás requiere sesión activa
                .anyRequest().authenticated()
            )

            // ── Manejo de errores HTTP correcto para APIs REST ────────────────────
            // Sin esto Spring Security puede devolver 302 (redirect) en vez de 401
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, e) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
            )

            // ── Logout ────────────────────────────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ── Gestión de sesión ─────────────────────────────────────────────────
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .sessionRegistry(sessionRegistry)
                .maxSessionsPreventsLogin(false)
            );

        return http.build();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
