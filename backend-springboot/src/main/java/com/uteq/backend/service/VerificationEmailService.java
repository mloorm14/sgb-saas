package com.uteq.backend.service;

import com.uteq.backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Código de verificación de correo al registrarse: valor efímero en Redis
 * con TTL, sin tabla nueva en Postgres.
 */
@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailService.class);
    private static final String KEY_PREFIX = "verificacion-correo:";
    private static final int LONGITUD_CODIGO = 6;

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.verificacion-correo.ttl-minutes}")
    private long ttlMinutes;

    /**
     * Genera o entrega generate ysend code a partir de los datos actuales del sistema.
     *
     * @param user valor de entrada user usado por la operacion para completar su regla de negocio
     */
    public void generateYSendCode(User user) {
        String code = generateCode();
        try {
            redisTemplate.opsForValue().set(key(user.getEmail()), code, Duration.ofMinutes(ttlMinutes));
        } catch (DataAccessException e) {
            log.error("Redis no disponible al generar código de verificación para {}", user.getEmail(), e);
            throw new ServiceTemporalmenteNotAvailableException(
                    "El servicio de verificación de correo no está disponible temporalmente. Intente más tarde.");
        }

        String body = "<p>Hola " + user.getName() + ",</p>"
                + "<p>Tu código de verificación es: <b>" + code + "</b></p>"
                + "<p>Vence en " + ttlMinutes + " minutos.</p>";
        boolean enviado = emailService.sendEmail(user.getEmail(), "Verifica tu correo - SGB-SaaS", body);
        if (!enviado) {
            // Fallo de SMTP no rompe el registro; queda como deuda operativa (reenviar).
            log.warn("No se pudo enviar el código de verificación a {}", user.getEmail());
        }
    }

    /**
     * Verifica validate y devuelve el resultado de la comprobacion.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param codeIngresado valor de entrada codeIngresado usado por la operacion para completar su regla de negocio
     */
    public void validate(String email, String codeIngresado) {
        String key = key(email);
        String codeAlmacenado;
        try {
            codeAlmacenado = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.error("Redis no disponible al validar código de verificación para {}", email, e);
            throw new CodeVerificationInvalidException(
                    "El servicio de verificación no está disponible temporalmente. Intente más tarde.");
        }

        if (codeAlmacenado == null) {
            throw new CodeVerificationInvalidException(
                    "El código expiró o no se ha solicitado uno para este correo.");
        }
        if (!codeAlmacenado.equals(codeIngresado)) {
            throw new CodeVerificationInvalidException("El código ingresado es incorrecto.");
        }

        // Un solo uso: se borra apenas se valida.
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al eliminar código ya validado para {}", email, e);
        }
    }

    private String generateCode() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%0" + LONGITUD_CODIGO + "d", value);
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }
}
