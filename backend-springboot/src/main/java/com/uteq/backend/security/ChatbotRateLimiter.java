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
 * Contador de mensajes del chatbot por usuario en Redis. Clave solo por
 * usuarioId (control de costo); ante caída de Redis degrada a fail-open.
 */
@Component
@RequiredArgsConstructor
public class ChatbotRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ChatbotRateLimiter.class);

    private static final String KEY_PREFIX = "chatbot-mensajes:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.gemini.rate-limit-max-mensajes}")
    private int maxMensajes;

    @Value("${app.gemini.rate-limit-window-seconds}")
    private long rateLimitWindowSeconds;

    /**
     * Handles esta Bloqueado.
     *
     * @param userId numeric identifier used to scope this esta Bloqueado
     * @return true when the check succeeds
     */

    public boolean estaBlocked(Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(key(userId));
            return value != null && Long.parseLong(value) >= maxMensajes;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en estaBloqueado (fail-open, sin bloqueo): usuarioId={}", userId, e);
            return false;
        }
    }

    /**
     * Incrementa el contador. El TTL de la ventana se fija solo en el
     * primer mensaje (cuando el contador pasa de 0 a 1), misma lógica de
     * ventana fija que LoginRateLimiter.registrarFallo.
     */
    public void registerMessage(Long userId) {
        try {
            String key = key(userId);
            Long freshValue = redisTemplate.opsForValue().increment(key);
            if (freshValue != null && freshValue == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(rateLimitWindowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en registrarMensaje (contador no incrementado): usuarioId={}", userId, e);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
