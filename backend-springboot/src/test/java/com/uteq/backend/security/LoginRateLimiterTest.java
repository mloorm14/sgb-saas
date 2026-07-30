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

    private static final String CORREO = "lector@uteq.edu.ec";
    private static final String IP = "10.0.0.1";
    private static final String KEY = "login-attempts:" + CORREO + ":" + IP;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginRateLimiter loginRateLimiter;

    private void configurarLimites(int maxAttempts, long ventanaSegundos) {
        ReflectionTestUtils.setField(loginRateLimiter, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(loginRateLimiter, "rateLimitWindowSeconds", ventanaSegundos);
    }

    // Escenario del 6to intento de la auditoría OWASP A07: con
    // maxAttempts=5 (default de app.security.login.max-attempts), un
    // contador que ya vale "5" (5 fallos previos) debe reportar bloqueado
    // -- ese es exactamente el estado antes del 6to intento.
    @Test
    void estaBloqueado_cuandoContadorIgualaElMaximo_retornaTrue() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertTrue(loginRateLimiter.estaBloqueado(CORREO, IP));
    }

    @Test
    void estaBloqueado_cuandoContadorPorDebajoDelMaximo_retornaFalse() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("4");

        assertFalse(loginRateLimiter.estaBloqueado(CORREO, IP));
    }

    @Test
    void estaBloqueado_sinIntentosPrevios_retornaFalse() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertFalse(loginRateLimiter.estaBloqueado(CORREO, IP));
    }

    // El TTL de la ventana se fija SOLO quando el contador pasa de 0 a 1
    // (primer fallo) -- no en cada incremento subsiguiente.
    @Test
    void registrarFallo_primerIntento_fijaTtlDeLaVentana() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        loginRateLimiter.registrarFallo(CORREO, IP);

        verify(redisTemplate).expire(KEY, Duration.ofSeconds(900));
    }

    @Test
    void registrarFallo_intentoSubsiguiente_noRefijaElTtl() {
        configurarLimites(5, 900);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(3L);

        loginRateLimiter.registrarFallo(CORREO, IP);

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void resetear_borraLaClave() {
        loginRateLimiter.resetear(CORREO, IP);

        verify(redisTemplate).delete(KEY);
    }
}
