package com.uteq.backend.dto;

import java.time.OffsetDateTime;

/**
 * Entrada del historial de una sesión (GET /api/v1/chatbot/sesiones/{id}/historial).
 * Se usa un DTO distinto de {@link MensajeChatResponseDTO} a propósito: el
 * historial necesita exponer QUIÉN escribió cada mensaje (rol USUARIO /
 * ASISTENTE) para que el frontend renderice la burbuja del lado correcto,
 * y {@code MensajeChatResponseDTO} no lleva ese campo -- reutilizarlo
 * perdería información de presentación que es esencial acá.
 */
public record MensajeChatHistorialDTO(
        String rol,
        String contenido,
        OffsetDateTime creadoEn
) {}
