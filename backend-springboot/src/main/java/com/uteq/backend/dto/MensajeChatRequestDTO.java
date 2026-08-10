package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body de POST /api/v1/chatbot/mensajes. {@code sesionId} es nullable: si
 * llega null, ChatbotService crea una sesión nueva para el usuario
 * autenticado; si viene poblado, debe ser una sesión del propio usuario
 * (si no, SesionChatNoEncontradaException). {@code texto} es el mensaje del
 * lector hacia el asistente.
 */
public record MensajeChatRequestDTO(

        UUID sesionId,

        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
        String texto
) {}
