package com.uteq.backend.service;

import com.uteq.backend.entity.Usuario;
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
public class VerificacionCorreoService {

    private static final Logger log = LoggerFactory.getLogger(VerificacionCorreoService.class);
    private static final String KEY_PREFIX = "verificacion-correo:";
    private static final int LONGITUD_CODIGO = 6;

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.verificacion-correo.ttl-minutes}")
    private long ttlMinutes;

    /**
     * Genera un código de 6 dígitos, lo guarda en Redis con TTL y lo envía
     * por correo. Un código nuevo sobrescribe al anterior sin usar.
     *
     * @throws ServicioTemporalmenteNoDisponibleException si Redis no permite GUARDAR (503).
     */
    public void generarYEnviarCodigo(Usuario usuario) {
        String codigo = generarCodigo();
        try {
            redisTemplate.opsForValue().set(key(usuario.getCorreo()), codigo, Duration.ofMinutes(ttlMinutes));
        } catch (DataAccessException e) {
            log.error("Redis no disponible al generar código de verificación para {}", usuario.getCorreo(), e);
            throw new ServicioTemporalmenteNoDisponibleException(
                    "El servicio de verificación de correo no está disponible temporalmente. Intente más tarde.");
        }

        String cuerpo = "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>Tu código de verificación es: <b>" + codigo + "</b></p>"
                + "<p>Vence en " + ttlMinutes + " minutos.</p>";
        boolean enviado = emailService.enviarCorreo(usuario.getCorreo(), "Verifica tu correo - SGB-SaaS", cuerpo);
        if (!enviado) {
            // Fallo de SMTP no rompe el registro; queda como deuda operativa (reenviar).
            log.warn("No se pudo enviar el código de verificación a {}", usuario.getCorreo());
        }
    }

    /**
     * @throws CodigoVerificacionInvalidoException si el código no coincide, expiró
     *                                              o Redis está caído (fail-closed).
     */
    public void validar(String correo, String codigoIngresado) {
        String llave = key(correo);
        String codigoAlmacenado;
        try {
            codigoAlmacenado = redisTemplate.opsForValue().get(llave);
        } catch (DataAccessException e) {
            log.error("Redis no disponible al validar código de verificación para {}", correo, e);
            throw new CodigoVerificacionInvalidoException(
                    "El servicio de verificación no está disponible temporalmente. Intente más tarde.");
        }

        if (codigoAlmacenado == null) {
            throw new CodigoVerificacionInvalidoException(
                    "El código expiró o no se ha solicitado uno para este correo.");
        }
        if (!codigoAlmacenado.equals(codigoIngresado)) {
            throw new CodigoVerificacionInvalidoException("El código ingresado es incorrecto.");
        }

        // Un solo uso: se borra apenas se valida.
        try {
            redisTemplate.delete(llave);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al eliminar código ya validado para {}", correo, e);
        }
    }

    private String generarCodigo() {
        int valor = secureRandom.nextInt(1_000_000);
        return String.format("%0" + LONGITUD_CODIGO + "d", valor);
    }

    private String key(String correo) {
        return KEY_PREFIX + correo;
    }
}
