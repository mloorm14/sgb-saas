package com.uteq.backend.service;

/**
 * Límite de mensajes al chatbot excedido → HTTP 429.
 */
public class ChatbotRateLimitExcedidoException extends RuntimeException {

    public ChatbotRateLimitExcedidoException(String message) {
        super(message);
    }
}
