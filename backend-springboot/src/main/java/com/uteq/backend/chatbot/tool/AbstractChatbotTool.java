package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;

/**
 * Base abstracta para TODAS las tools del chatbot.
 * Centraliza el {@link ObjectMapper} compartido y el helper de
 * nodos de error (antes duplicados en cada tool concreta).
 * No lleva {@code @Component}: Spring solo registra las subclases
 * concretas vía {@code List<ChatbotTool>} en el registry.
 */
public abstract class AbstractChatbotTool implements ChatbotTool {

    protected final ObjectMapper mapper = new ObjectMapper();

    protected ObjectNode errorNode(String mensaje) {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", mensaje);
        return error;
    }
}
