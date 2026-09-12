package com.uteq.backend.service;

/**
 * Dependencia externa no disponible (ej. Redis caído) → HTTP 503 para reintentar más tarde.
 */
public class ServiceTemporalmenteNotAvailableException extends RuntimeException {

    public ServiceTemporalmenteNotAvailableException(String message) {
        super(message);
    }
}
