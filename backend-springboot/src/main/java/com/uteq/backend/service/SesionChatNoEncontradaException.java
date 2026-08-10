package com.uteq.backend.service;

/**
 * Módulo H: la sesión de chat solicitada no existe o pertenece a otro
 * usuario (ChatbotService valida propiedad de la sesión, un LECTOR solo
 * puede leer/escribir en sus propias sesiones). Se traduce en HTTP 404 por
 * GlobalExceptionHandler.
 */
public class SesionChatNoEncontradaException extends RuntimeException {

    public SesionChatNoEncontradaException(String message) {
        super(message);
    }
}
