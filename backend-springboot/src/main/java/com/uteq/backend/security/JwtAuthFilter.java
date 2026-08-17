package com.uteq.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            if (!jwtService.validateToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            String jti = jwtService.extractJti(token);
            try {
                if (estaRevocado(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (DataAccessException e) {
                // Fail-closed (decisión OWASP A07, ver
                // docs/mediciones/sec/owasp/decision-fail-closed-jwt-redis.md):
                // no poder consultar la blacklist no equivale a "token vigente";
                // un token revocado (logout, cambio de contraseña, cuenta
                // bloqueada) tratado como válido durante el corte restablece
                // accesos que ya fueron removidos. Se rechaza con 401 y no se
                // continúa la cadena del filtro. El log es best-effort (no
                // depende de Redis) para distinguir en monitoreo un outage de
                // infraestructura de una revocación real.
                SecurityContextHolder.clearContext();
                log.error("Redis no disponible al verificar revocación (fail-closed, request rechazada con 401): jti={}", jti, e);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                // ProblemDetail serializado a mano: este filtro corre antes del
                // despacho MVC, así que no depende de un ObjectMapper inyectado
                // (que tampoco existe en contextos de test tipo @WebMvcTest).
                response.getWriter().write(
                        "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                                + "\"detail\":\"No se pudo confirmar la validez del token. Intente nuevamente.\"}"
                );
                return;
            }

            String correo = jwtService.extractCorreo(token);
            UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(correo);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * La revocación por logout vive en la blacklist de Redis. Consulta directa
     * sin degradación local: si Redis no responde, la {@link DataAccessException}
     * se propaga al catch de {@link #doFilterInternal} que la maneja en
     * fail-closed (401). Ver
     * docs/mediciones/sec/owasp/decision-fail-closed-jwt-redis.md.
     */
    private boolean estaRevocado(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti));
    }
}
