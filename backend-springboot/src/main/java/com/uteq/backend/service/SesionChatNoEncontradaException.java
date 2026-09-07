package com.uteq.backend.service;

/**
 * Sesión de chat inexistente o de otro usuario → HTTP 404.
 */
public class SesionChatNoEncontradaException extends RuntimeException {

    public SesionChatNoEncontradaException(String message) {
        super(message);
    }
}
