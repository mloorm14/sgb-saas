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

     * Executes the estaBloqueado operation.

     * @param usuarioId value required by the operation

     * @return operation result

     */

    public boolean estaBloqueado(Long usuarioId) {
        try {
            String valor = redisTemplate.opsForValue().get(key(usuarioId));
            return valor != null && Long.parseLong(valor) >= maxMensajes;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en estaBloqueado (fail-open, sin bloqueo): usuarioId={}", usuarioId, e);
            return false;
        }
    }

    /**
     * Incrementa el contador. El TTL de la ventana se fija solo en el
     * primer mensaje (cuando el contador pasa de 0 a 1), misma lógica de
     * ventana fija que LoginRateLimiter.registrarFallo.
     */
    public void registrarMensaje(Long usuarioId) {
        try {
            String llave = key(usuarioId);
            Long nuevoValor = redisTemplate.opsForValue().increment(llave);
            if (nuevoValor != null && nuevoValor == 1L) {
                redisTemplate.expire(llave, Duration.ofSeconds(rateLimitWindowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible en registrarMensaje (contador no incrementado): usuarioId={}", usuarioId, e);
        }
    }

    private String key(Long usuarioId) {
        return KEY_PREFIX + usuarioId;
    }
}
