package com.uteq.backend.service;

/**
 * Sesión de chat inexistente o de otro usuario → HTTP 404.
 */
public class SessionChatNotFoundException extends RuntimeException {

    public SessionChatNotFoundException(String message) {
        super(message);
    }
}
