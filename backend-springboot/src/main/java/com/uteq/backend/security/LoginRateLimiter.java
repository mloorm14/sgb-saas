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

     * Executes the estaBloqueado operation.

     * @param correo value required by the operation

     * @param ip value required by the operation

     * @return operation result

     */

    public boolean estaBloqueado(String correo, String ip) {
        try {
            String valor = redisTemplate.opsForValue().get(key(correo, ip));
            return valor != null && Long.parseLong(valor) >= maxAttempts;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en estaBloqueado (fail-open, sin bloqueo): correo={}", correo, e);
            return false;
        }
    }

    /**
     * Incrementa el contador. El TTL de la ventana se fija solo en el
     * primer intento fallido (cuando el contador pasa de 0 a 1) -- así la
     * ventana es una ventana fija desde el primer fallo, no se renueva en
     * cada intento subsiguiente (evita que un atacante lento mantenga el
     * bloqueo indefinidamente fallando un intento cada pocos minutos).
     */
    public void registrarFallo(String correo, String ip) {
        try {
            String llave = key(correo, ip);
            Long nuevoValor = redisTemplate.opsForValue().increment(llave);
            if (nuevoValor != null && nuevoValor == 1L) {
                redisTemplate.expire(llave, Duration.ofSeconds(rateLimitWindowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en registrarFallo (contador no incrementado): correo={}", correo, e);
        }
    }

    /**

     * Executes the resetear operation.

     * @param correo value required by the operation

     * @param ip value required by the operation

     */

    public void resetear(String correo, String ip) {
        try {
            redisTemplate.delete(key(correo, ip));
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en resetear (contador no limpiado): correo={}", correo, e);
        }
    }

    /**
     * Segundos restantes de la ventana de bloqueo, para informar al
     * usuario cuánto debe esperar. Devuelve el TTL de la ventana completa
     * si por alguna razón Redis no expone un TTL preciso (ej. -1/-2 de
     * {@code getExpire}), en vez de un número negativo confuso.
     */
    public long segundosRestantes(String correo, String ip) {
        try {
            Long ttl = redisTemplate.getExpire(key(correo, ip));
            return (ttl == null || ttl < 0) ? rateLimitWindowSeconds : ttl;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en segundosRestantes (se informa ventana completa): correo={}", correo, e);
            return rateLimitWindowSeconds;
        }
    }

    private String key(String correo, String ip) {
        return KEY_PREFIX + correo + ":" + ip;
    }
}
