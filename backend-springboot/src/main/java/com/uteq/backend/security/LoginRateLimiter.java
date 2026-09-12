package com.uteq.backend.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Contador de intentos fallidos de login en Redis. Clave por correo+IP
 * para no bloquear al dueño por intentos ajenos; ante caída de Redis
 * degrada a fail-open sin romper el login.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final String KEY_PREFIX = "login-attempts:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.login.max-attempts}")
    private int maxAttempts;

    @Value("${app.security.login.rate-limit-window-seconds}")
    private long rateLimitWindowSeconds;

    /**
     * Procesa esta blocked y devuelve el resultado calculado por el backend.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param ip valor de entrada ip usado por la operacion para completar su regla de negocio
     * @return true cuando la comprobacion se cumple; false en caso contrario
     */

    public boolean estaBlocked(String email, String ip) {
        try {
            String value = redisTemplate.opsForValue().get(key(email, ip));
            return value != null && Long.parseLong(value) >= maxAttempts;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en estaBloqueado (fail-open, sin bloqueo): correo={}", email, e);
            return false;
        }
    }

    /**
     * Registra register failure validando los datos de entrada antes de persistir cambios.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param ip valor de entrada ip usado por la operacion para completar su regla de negocio
     */
    public void registerFailure(String email, String ip) {
        try {
            String key = key(email, ip);
            Long freshValue = redisTemplate.opsForValue().increment(key);
            if (freshValue != null && freshValue == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(rateLimitWindowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en registrarFallo (contador no incrementado): correo={}", email, e);
        }
    }

    /**
     * Ejecuta resetear aplicando las validaciones necesarias del proceso.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param ip valor de entrada ip usado por la operacion para completar su regla de negocio
     */

    public void resetear(String email, String ip) {
        try {
            redisTemplate.delete(key(email, ip));
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en resetear (contador no limpiado): correo={}", email, e);
        }
    }

    /**
     * Procesa seconds restantes y devuelve el resultado calculado por el backend.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param ip valor de entrada ip usado por la operacion para completar su regla de negocio
     * @return valor numerico calculado o recuperado por la operacion
     */
    public long secondsRestantes(String email, String ip) {
        try {
            Long ttl = redisTemplate.getExpire(key(email, ip));
            return (ttl == null || ttl < 0) ? rateLimitWindowSeconds : ttl;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en segundosRestantes (se informa ventana completa): correo={}", email, e);
            return rateLimitWindowSeconds;
        }
    }

    private String key(String email, String ip) {
        return KEY_PREFIX + email + ":" + ip;
    }
}
