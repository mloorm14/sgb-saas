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
            if (estaRevocado(jti)) {
                filterChain.doFilter(request, response);
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
     * La revocación por logout vive en la blacklist de Redis. Antes de
     * envolverla, una caída de Redis hacía que esta consulta cayera en el
     * catch general de abajo: se limpiaba el contexto de seguridad y TODA
     * request autenticada respondía 403 durante el corte (la firma JWT era
     * válida, pero el filtro nunca llegaba a autenticar). Fail-open (tratar
     * como no revocado): la firma y el exp siguen validados; solo se degrada
     * la revocación puntual, acotada por el TTL del token. El corte queda en
     * warn. Ver docs/mediciones/sec/2026-08-14-incidente-500-auth-redis-produccion.md.
     */
    private boolean estaRevocado(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti));
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en estaRevocado (fail-open, token tratado como vigente): jti={}", jti, e);
            return false;
        }
    }
}
