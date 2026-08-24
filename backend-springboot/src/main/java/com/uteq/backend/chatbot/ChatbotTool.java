package com.uteq.backend.chatbot;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface funcional que representa una "herramienta" (tool) que el chatbot
 * puede invocar. Cada tool concreta se anota como {@code @Component} y se
 * auto-registra en {@link ChatbotToolRegistry}.
 * <p>
 * El patrón es similar a Spring Security's {@code GrantedAuthority}: cada
 * tool declara su nombre, descripción y schema de entrada, y el registry las
 * recolecta para pasarlas a Gemini en cada llamada.
 */
public interface ChatbotTool {

    /** Nombre único de la tool (Gemini lo usa para identificar la función). */
    String getName();

    /** Descripción legible por humanos que Gemini usa para decidir cuándo invocar la tool. */
    String getDescription();

    /**
     * JSON Schema del input de la tool. Ejemplo:
     * {@code { "type": "object", "properties": { "query": { "type": "string" } }, "required": ["query"] }}
     */
    JsonNode getInputSchema();

    /**
     * Ejecuta la tool con los argumentos proporcionados por Gemini.
     * @param args argumentos parseados del functionCall de Gemini
     * @return resultado serializable como JSON que se devuelve a Gemini
     */
    JsonNode execute(JsonNode args);
}
