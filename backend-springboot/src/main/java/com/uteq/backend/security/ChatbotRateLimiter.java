package com.uteq.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Contador de mensajes del chatbot por usuario en Redis (Módulo H). Mismo
 * mecanismo que {@link LoginRateLimiter} (ver su Javadoc para la lógica de
 * TTL fijado solo en el primer incremento).
 * <p>
 * A diferencia de login, la clave es SOLO {@code "chatbot-mensajes:" +
 * usuarioId} y no incluye IP: el chatbot no es un vector de fuerza bruta
 * contra la CUENTA de otro usuario (nadie escribe "su" correo en el chat
 * de otra persona), sino un control de costo por usuario autenticado --
 * cuántas llamadas a la API de Gemini puede disparar un LECTOR en una
 * ventana. Combinar la IP acá no aportaría y castigaría a usuarios legítimos
 * detrás de NAT compartido.
 */
@Component
@RequiredArgsConstructor
public class ChatbotRateLimiter {

    private static final String KEY_PREFIX = "chatbot-mensajes:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.gemini.rate-limit-max-mensajes}")
    private int maxMensajes;

    @Value("${app.gemini.rate-limit-window-seconds}")
    private long rateLimitWindowSeconds;

    public boolean estaBloqueado(Long usuarioId) {
        String valor = redisTemplate.opsForValue().get(key(usuarioId));
        return valor != null && Long.parseLong(valor) >= maxMensajes;
    }

    /**
     * Incrementa el contador. El TTL de la ventana se fija solo en el
     * primer mensaje (cuando el contador pasa de 0 a 1), misma lógica de
     * ventana fija que LoginRateLimiter.registrarFallo.
     */
    public void registrarMensaje(Long usuarioId) {
        String llave = key(usuarioId);
        Long nuevoValor = redisTemplate.opsForValue().increment(llave);
        if (nuevoValor != null && nuevoValor == 1L) {
            redisTemplate.expire(llave, Duration.ofSeconds(rateLimitWindowSeconds));
        }
    }

    private String key(Long usuarioId) {
        return KEY_PREFIX + usuarioId;
    }
}
