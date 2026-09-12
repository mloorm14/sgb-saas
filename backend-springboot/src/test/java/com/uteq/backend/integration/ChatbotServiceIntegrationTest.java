package com.uteq.backend.integration;

import com.uteq.backend.entity.MessageChat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración REAL de GeminiClient contra la API de generateContent
 * (Módulo H). Deshabilitado por defecto a propósito:
 *
 * <pre>
 *   - En CI NUNCA debe correr: consume cuota real de la API de Google y
 *     depende de una GEMINI_API_KEY real (la de ci.yml es
 *     dummy-key-para-ci, con la que Gemini responde 400 y el cliente cae
 *     al mensaje de fallback).
 *   - Se ejecuta SOLO manualmente: quitar el @Disabled, exportar una
 *     GEMINI_API_KEY real (Google AI Studio) y correr
 *     {@code mvnw -Dtest=ChatbotServiceIntegrationTest test}.
 * </pre>
 *
 * Los tests unitarios de ChatbotService mockean GeminiClient (ver
 * ChatbotServiceTest), así que el pipeline de CI valida el flujo completo
 * sin tocar la API real.
 */
@Disabled("Requiere GEMINI_API_KEY real y consume cuota de la API de Gemini: "
        + "se ejecuta solo manualmente, nunca en CI.")
@SpringBootTest
class ChatbotServiceIntegrationTest {

    @Autowired
    private GeminiClient geminiClient;

    @Test
    void generateResponse_withPromptSimple_retornaTextModelo() {
        String response = geminiClient.generateResponse(
                "Responde con la palabra 'OK'.",
                List.of(),
                "Hola");

        assertThat(response)
                .isNotBlank()
                .isNotEqualTo("El asistente está saturado, intenta en unos segundos.")
                .isNotEqualTo("No se pudo obtener respuesta del asistente, intenta de nuevo.");
    }

    @Test
    void generateResponse_withHistory_retornaResponseConsistente() {
        MessageChat previo = new MessageChat();
        previo.setRole("USUARIO");
        previo.setContent("¿Cuál es el horario de la biblioteca?");

        String response = geminiClient.generateResponse(
                "Eres el asistente de una biblioteca. Responde con el contexto real provisto.",
                List.of(previo),
                "¿De lunes a viernes?");

        assertThat(response).isNotBlank();
    }
}
