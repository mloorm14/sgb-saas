package com.uteq.backend.controller;

import com.uteq.backend.chatbot.ChatbotOrchestrator;
import com.uteq.backend.dto.MensajeChatHistorialDTO;
import com.uteq.backend.dto.MensajeChatRequestDTO;
import com.uteq.backend.dto.MensajeChatResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Asistente virtual (Módulo H, chatbot con Gemini + function calling).
 * Restringido SOLO a LECTOR. Usa {@link ChatbotOrchestrator} que gestiona
 * el loop de function calling (Gemini → tool → resultado → Gemini → respuesta final).
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@Validated
@Tag(name = "Chatbot", description = "Asistente virtual con Gemini + function calling (Módulo H), solo LECTOR")
public class ChatbotController {

    private final ChatbotOrchestrator chatbotOrchestrator;

    public ChatbotController(ChatbotOrchestrator chatbotOrchestrator) {
        this.chatbotOrchestrator = chatbotOrchestrator;
    }

    // ── POST /api/v1/chatbot/mensajes ──────────────────────
    @PostMapping("/mensajes")
    @PreAuthorize("hasRole('LECTOR')")
    @Operation(summary = "Enviar mensaje al asistente",
            description = "Persiste el mensaje del LECTOR, lo envía a Gemini con function calling "
                    + "(herramientas reales que consultan la BD) y devuelve la respuesta del asistente. "
                    + "Si sesionId es null se crea una sesión nueva; si viene poblado debe ser del propio usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta del asistente"),
            @ApiResponse(responseCode = "400", description = "Mensaje vacío o mayor a 500 caracteres"),
            @ApiResponse(responseCode = "403", description = "No es LECTOR o no autenticado"),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada o de otro usuario"),
            @ApiResponse(responseCode = "429", description = "Límite de mensajes por minuto excedido")
    })
    public ResponseEntity<MensajeChatResponseDTO> enviarMensaje(
            @Valid @RequestBody MensajeChatRequestDTO dto, Authentication authentication) {
        return ResponseEntity.ok(chatbotOrchestrator.enviarMensaje(dto, authentication));
    }

    // ── GET /api/v1/chatbot/sesiones/{id}/historial ────────
    @GetMapping("/sesiones/{id}/historial")
    @PreAuthorize("hasRole('LECTOR')")
    @Operation(summary = "Historial de una sesión de chat",
            description = "Devuelve los mensajes de la sesión ordenados cronológicamente, indicando "
                    + "rol (USUARIO/ASISTENTE), contenido y timestamp. Solo el dueño de la sesión puede leerlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de la sesión"),
            @ApiResponse(responseCode = "403", description = "No es LECTOR o no autenticado"),
            @ApiResponse(responseCode = "404", description = "Sesión no encontrada o de otro usuario")
    })
    public ResponseEntity<List<MensajeChatHistorialDTO>> historial(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(chatbotOrchestrator.obtenerHistorial(id, authentication));
    }
}
