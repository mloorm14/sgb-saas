package com.uteq.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Contador de intentos fallidos de login en Redis, para OWASP A07 (Bloque
 * C.2 -- ver docs/mediciones/sec/2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md
 * para el gap original que esto cierra).
 * <p>
 * Clave compuesta por {@code correo + ip} (no solo {@code correo}) a
 * propósito: si la clave fuera solo el correo, un atacante podría agotar el
 * cupo de intentos de la CUENTA DE OTRA PERSONA fallando login repetidas
 * veces contra su correo desde IPs propias, bloqueando al dueño legítimo de
 * esa cuenta sin necesidad de conocer su contraseña -- un vector de
 * denegación de servicio nuevo, peor que el problema que se está
 * resolviendo. Con la clave combinada, el contador de la IP real del
 * usuario legítimo nunca se ve afectado por intentos fallidos originados en
 * IPs ajenas contra el mismo correo.
 * <p>
 * Limitación aceptada y documentada (no resuelta acá): un atacante que
 * controle múltiples IPs (proxies/botnet) puede seguir intentando fuerza
 * bruta contra un mismo correo rotando de IP cada {@code maxAttempts}
 * intentos -- cada combinación correo+IP tiene su propio cupo independiente.
 * Mitigar esto requeriría una capa adicional (reputación de IP, CAPTCHA,
 * límite global por correo con ventana más laxa) fuera del alcance de esta
 * entrega; se documenta como riesgo aceptado, no como algo resuelto.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String KEY_PREFIX = "login-attempts:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.login.max-attempts}")
    private int maxAttempts;

    @Value("${app.security.login.rate-limit-window-seconds}")
    private long rateLimitWindowSeconds;

    public boolean estaBloqueado(String correo, String ip) {
        String valor = redisTemplate.opsForValue().get(key(correo, ip));
        return valor != null && Long.parseLong(valor) >= maxAttempts;
    }

    /**
     * Incrementa el contador. El TTL de la ventana se fija solo en el
     * primer intento fallido (cuando el contador pasa de 0 a 1) -- así la
     * ventana es una ventana fija desde el primer fallo, no se renueva en
     * cada intento subsiguiente (evita que un atacante lento mantenga el
     * bloqueo indefinidamente fallando un intento cada pocos minutos).
     */
    public void registrarFallo(String correo, String ip) {
        String llave = key(correo, ip);
        Long nuevoValor = redisTemplate.opsForValue().increment(llave);
        if (nuevoValor != null && nuevoValor == 1L) {
            redisTemplate.expire(llave, Duration.ofSeconds(rateLimitWindowSeconds));
        }
    }

    public void resetear(String correo, String ip) {
        redisTemplate.delete(key(correo, ip));
    }

    /**
     * Segundos restantes de la ventana de bloqueo, para informar al
     * usuario cuánto debe esperar. Devuelve el TTL de la ventana completa
     * si por alguna razón Redis no expone un TTL preciso (ej. -1/-2 de
     * {@code getExpire}), en vez de un número negativo confuso.
     */
    public long segundosRestantes(String correo, String ip) {
        Long ttl = redisTemplate.getExpire(key(correo, ip));
        return (ttl == null || ttl < 0) ? rateLimitWindowSeconds : ttl;
    }

    private String key(String correo, String ip) {
        return KEY_PREFIX + correo + ":" + ip;
    }
}
