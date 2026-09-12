package com.uteq.backend.service;

import com.uteq.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEmailServiceTest {

    private static final String CORREO = "lector@correo.com";
    private static final String KEY = "verificacion-correo:" + CORREO;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailService emailService;

    private VerificationEmailService verificationEmailService;

    private VerificationEmailService construir() {
        VerificationEmailService service = new VerificationEmailService(redisTemplate, emailService);
        ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
        return service;
    }

    private User userTest() {
        User user = new User();
        user.setName("Ana");
        user.setEmail(CORREO);
        return user;
    }

    @Test
    void generateYSendCode_guardaRedisWithTtlYEnviaEmail() {
        verificationEmailService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        verificationEmailService.generateYSendCode(userTest());

        ArgumentCaptor<String> codeCapturado = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), codeCapturado.capture(), eq(Duration.ofMinutes(10)));
        assertTrue(codeCapturado.getValue().matches("\\d{6}"));
        verify(emailService).sendEmail(eq(CORREO), anyString(), anyString());
    }

    // Fallo de envío no debe lanzar excepción -- ver Javadoc de
    // generarYEnviarCodigo: EmailService ya decidió que esto no rompe el
    // flujo de registro.
    @Test
    void generateYSendCode_fallaEnvio_notLanzaException() {
        verificationEmailService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> verificationEmailService.generateYSendCode(userTest()));
    }

    @Test
    void validate_codeCorrecto_borraKeyYNotLanza() {
        verificationEmailService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("123456");

        assertDoesNotThrow(() -> verificationEmailService.validate(CORREO, "123456"));

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void validate_codeIncorrecto_lanzaCodeVerificationInvalid() {
        verificationEmailService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("123456");

        assertThrows(CodeVerificationInvalidException.class,
                () -> verificationEmailService.validate(CORREO, "999999"));
    }

    @Test
    void validate_withoutCodeRedis_lanzaCodeVerificationInvalid() {
        verificationEmailService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThrows(CodeVerificationInvalidException.class,
                () -> verificationEmailService.validate(CORREO, "123456"));
    }
}