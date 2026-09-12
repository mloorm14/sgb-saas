package com.uteq.backend.service;

/**
 * Límite de mensajes al chatbot excedido → HTTP 429.
 */
public class ChatbotRateLimitExceededException extends RuntimeException {

    public ChatbotRateLimitExceededException(String message) {
        super(message);
    }
}
