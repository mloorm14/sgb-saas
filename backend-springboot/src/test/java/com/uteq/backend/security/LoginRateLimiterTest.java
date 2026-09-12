package com.uteq.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final String CORREO = "lector@correo.com";
    private static final String IP = "10.0.0.1";
    private static final String KEY = "login-attempts:" + CORREO + ":" + IP;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginRateLimiter loginRateLimiter;

    private void configurarLimites(int maxAttempts, long ventanaSeconds) {
        ReflectionTestUtils.setField(loginRateLimiter, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(loginRateLimiter, "rateLimitWindowSeconds", ventanaSeconds);
    }

    // Escenario del 6to intento de la auditoría OWASP A07: con
    // maxAttempts=5 (default de app.security.login.max-attempts), un
    // contador que ya vale "5" (5 fallos previos) debe reportar bloqueado
    // -- ese es exactamente el estado antes del 6to intento.
    @Test
    void estaBlocked_cuandoContadorIgualaMaximo_retornaTrue() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertTrue(loginRateLimiter.estaBlocked(CORREO, IP));
    }

    @Test
    void estaBlocked_cuandoContadorByDebajoMaximo_retornaFalse() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("4");

        assertFalse(loginRateLimiter.estaBlocked(CORREO, IP));
    }

    @Test
    void estaBlocked_withoutAttemptsPrevios_retornaFalse() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertFalse(loginRateLimiter.estaBlocked(CORREO, IP));
    }

    // El TTL de la ventana se fija SOLO quando el contador pasa de 0 a 1
    // (primer fallo) -- no en cada incremento subsiguiente.
    @Test
    void registerFailure_primerAttempt_fijaTtlVentana() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        loginRateLimiter.registerFailure(CORREO, IP);

        verify(redisTemplate).expire(KEY, Duration.ofSeconds(900));
    }

    @Test
    void registerFailure_attemptSubsiguiente_notRefijaTtl() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(3L);

        loginRateLimiter.registerFailure(CORREO, IP);

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void resetear_borraKey() {
        loginRateLimiter.resetear(CORREO, IP);

        verify(redisTemplate).delete(KEY);
    }
}
