package com.uteq.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta de POST /api/v1/chatbot/mensajes: la sesión sobre la que se
 * respondió (la existente o la recién creada), la respuesta del asistente
 * y el timestamp en que se persistió esa respuesta.
 */
public record MensajeChatResponseDTO(
        UUID sesionId,
        String respuesta,
        OffsetDateTime timestamp
) {}
