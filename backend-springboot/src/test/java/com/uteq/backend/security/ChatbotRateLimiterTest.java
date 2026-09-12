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

    private void configurarLimites(int maxMensajes, long ventanaSeconds) {
        ReflectionTestUtils.setField(chatbotRateLimiter, "maxMensajes", maxMensajes);
        ReflectionTestUtils.setField(chatbotRateLimiter, "rateLimitWindowSeconds", ventanaSeconds);
    }

    @Test
    void estaBlocked_cuandoContadorIgualaMaximo_retornaTrue() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("10");

        assertTrue(chatbotRateLimiter.estaBlocked(USUARIO_ID));
    }

    @Test
    void estaBlocked_cuandoContadorByDebajoMaximo_retornaFalse() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertFalse(chatbotRateLimiter.estaBlocked(USUARIO_ID));
    }

    @Test
    void estaBlocked_withoutMensajesPrevios_retornaFalse() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertFalse(chatbotRateLimiter.estaBlocked(USUARIO_ID));
    }

    // El TTL de la ventana se fija SOLO cuando el contador pasa de 0 a 1
    // (primer mensaje), misma lógica de ventana fija que LoginRateLimiter.
    @Test
    void registerMessage_primerMessage_fijaTtlVentana() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        chatbotRateLimiter.registerMessage(USUARIO_ID);

        verify(redisTemplate).expire(KEY, Duration.ofSeconds(60));
    }

    @Test
    void registerMessage_messageSubsiguiente_notRefijaTtl() {
        configurarLimites(10, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(3L);

        chatbotRateLimiter.registerMessage(USUARIO_ID);

        verify(redisTemplate, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
