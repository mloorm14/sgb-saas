package com.uteq.backend.config;

import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsServiceImpl);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/registro",
                                "/api/auth/verificar-correo",
                                "/api/auth/reenviar-codigo",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                // Portal público (Rama C): superficie de solo
                                // lectura del catálogo sin cuenta (ver
                                // PublicoLibroController). Angosta a propósito:
                                // solo /api/publico/libros, nada más.
                                "/api/publico/**",
                                "/api/docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        // OWASP A05 (Bloque C.2, REQ-NF-014): gap identificado en
                        // docs/mediciones/sec/2026-07-30-owasp-a05-mala-configuracion-seguridad.md
                        // ("Content-Security-Policy ausente en ambos" -- backend y
                        // frontend, causa independiente del gap de TLS). Se cierra
                        // acá para el backend: la única superficie HTML que sirve
                        // hoy es Swagger UI (deshabilitado en el perfil `prod`, ver
                        // application.yml y adr-015-tls-transporte.md), así que una
                        // política restrictiva no rompe ningún flujo de la API JSON.
                        // Si Swagger UI llegase a necesitar estilos/scripts inline en
                        // algún perfil de desarrollo, ampliar acá explícitamente en
                        // vez de relajar por defecto.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "script-src-attr 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: blob:; "
                                        + "connect-src 'self' https://sgb-backend-b058.onrender.com; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'none'; "
                                        + "object-src 'none'"
                        ))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "https://biblora-sgb.onrender.com"));
        // PATCH incluido desde el fix de CORS: los 3 endpoints PATCH
        // (sugerencias-adquisicion/{id}/estado, admin/usuarios/{id}/rol,
        // admin/usuarios/{id}/estado) morían en el preflight OPTIONS
        // cross-origin sin él -- un 403 de CORS antes de llegar al
        // @PreAuthorize, imposible de arreglar desde el frontend.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
