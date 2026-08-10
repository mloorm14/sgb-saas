package com.uteq.backend.service;

/**
 * Módulo H: el usuario autenticado excedió el máximo de mensajes al chatbot
 * por ventana de tiempo (ChatbotRateLimiter). Se traduce en HTTP 429 por
 * GlobalExceptionHandler, mismo criterio que LoginRateLimitExcedidoException.
 */
public class ChatbotRateLimitExcedidoException extends RuntimeException {

    public ChatbotRateLimitExcedidoException(String message) {
        super(message);
    }
}
