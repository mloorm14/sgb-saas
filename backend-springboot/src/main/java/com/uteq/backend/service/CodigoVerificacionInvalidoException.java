package com.uteq.backend.service;

/**
 * Lanzada por {@link VerificacionCorreoService#validar(String, String)}
 * cuando el código de 6 dígitos no coincide con el almacenado en Redis, o
 * cuando ya no existe (expiró su TTL o nunca se solicitó uno para ese
 * correo). Mismo patrón que {@link PrestamoVencidoException}: excepción de
 * dominio propia para que {@code GlobalExceptionHandler} la traduzca a un
 * status HTTP explícito (400) en vez de caer en el handler genérico.
 */
public class CodigoVerificacionInvalidoException extends RuntimeException {

    public CodigoVerificacionInvalidoException(String mensaje) {
        super(mensaje);
    }
}
