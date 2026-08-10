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

// Módulo H (chatbot): mismo patrón de test que LoginRateLimiterTest, sobre
// la clave "chatbot-mensajes:<usuarioId>".
@ExtendWith(MockitoExtension.class)
class ChatbotRateLimiterTest {

    private static final Long USUARIO_ID = 1L;
    private static final String KEY = "chatbot-mensajes:" + USUARIO_ID;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ChatbotRateLimiter chatbotRateLimiter;

    private void configurarLimites(int maxMensajes, long ventanaSegundos) {
        ReflectionTestUtils.setField(chatbotRateLimiter, "maxMensajes", maxMensajes);
        ReflectionTestUtils.setField(chatbotRateLimiter, "rateLimitWindowSeconds", ventanaSegundos);
    }

    @Test
    void estaBloqueado_cuandoContadorIgualaElMaximo_retornaTrue() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("10");

        assertTrue(chatbotRateLimiter.estaBloqueado(USUARIO_ID));
    }

    @Test
    void estaBloqueado_cuandoContadorPorDebajoDelMaximo_retornaFalse() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertFalse(chatbotRateLimiter.estaBloqueado(USUARIO_ID));
    }

    @Test
    void estaBloqueado_sinMensajesPrevios_retornaFalse() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertFalse(chatbotRateLimiter.estaBloqueado(USUARIO_ID));
    }

    // El TTL de la ventana se fija SOLO cuando el contador pasa de 0 a 1
    // (primer mensaje), misma lógica de ventana fija que LoginRateLimiter.
    @Test
    void registrarMensaje_primerMensaje_fijaTtlDeLaVentana() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        chatbotRateLimiter.registrarMensaje(USUARIO_ID);

        verify(redisTemplate).expire(KEY, Duration.ofSeconds(60));
    }

    @Test
    void registrarMensaje_mensajeSubsiguiente_noRefijaElTtl() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(3L);

        chatbotRateLimiter.registrarMensaje(USUARIO_ID);

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
