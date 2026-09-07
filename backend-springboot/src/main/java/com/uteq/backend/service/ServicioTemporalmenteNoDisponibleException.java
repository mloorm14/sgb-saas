package com.uteq.backend.service;

/**
 * Dependencia externa no disponible (ej. Redis caído) → HTTP 503 para reintentar más tarde.
 */
public class ServicioTemporalmenteNoDisponibleException extends RuntimeException {

    public ServicioTemporalmenteNoDisponibleException(String message) {
        super(message);
    }
}
