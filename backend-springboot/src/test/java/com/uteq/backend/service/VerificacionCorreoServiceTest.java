package com.uteq.backend.service;

import com.uteq.backend.entity.Usuario;
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
class VerificacionCorreoServiceTest {

    private static final String CORREO = "lector@uteq.edu.ec";
    private static final String KEY = "verificacion-correo:" + CORREO;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailService emailService;

    private VerificacionCorreoService verificacionCorreoService;

    private VerificacionCorreoService construir() {
        VerificacionCorreoService service = new VerificacionCorreoService(redisTemplate, emailService);
        ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
        return service;
    }

    private Usuario usuarioDePrueba() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setCorreo(CORREO);
        return usuario;
    }

    @Test
    void generarYEnviarCodigo_guardaEnRedisConTtlYEnviaCorreo() {
        verificacionCorreoService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(emailService.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);

        verificacionCorreoService.generarYEnviarCodigo(usuarioDePrueba());

        ArgumentCaptor<String> codigoCapturado = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), codigoCapturado.capture(), eq(Duration.ofMinutes(10)));
        assertTrue(codigoCapturado.getValue().matches("\\d{6}"));
        verify(emailService).enviarCorreo(eq(CORREO), anyString(), anyString());
    }

    // Fallo de envío no debe lanzar excepción -- ver Javadoc de
    // generarYEnviarCodigo: EmailService ya decidió que esto no rompe el
    // flujo de registro.
    @Test
    void generarYEnviarCodigo_fallaElEnvio_noLanzaExcepcion() {
        verificacionCorreoService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(emailService.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> verificacionCorreoService.generarYEnviarCodigo(usuarioDePrueba()));
    }

    @Test
    void validar_codigoCorrecto_borraLaClaveYNoLanza() {
        verificacionCorreoService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("123456");

        assertDoesNotThrow(() -> verificacionCorreoService.validar(CORREO, "123456"));

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void validar_codigoIncorrecto_lanzaCodigoVerificacionInvalido() {
        verificacionCorreoService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("123456");

        assertThrows(CodigoVerificacionInvalidoException.class,
                () -> verificacionCorreoService.validar(CORREO, "999999"));
    }

    @Test
    void validar_sinCodigoEnRedis_lanzaCodigoVerificacionInvalido() {
        verificacionCorreoService = construir();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThrows(CodigoVerificacionInvalidoException.class,
                () -> verificacionCorreoService.validar(CORREO, "123456"));
    }
}