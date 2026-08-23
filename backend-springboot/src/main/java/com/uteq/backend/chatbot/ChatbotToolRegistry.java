package com.uteq.backend.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry que recolecta todas las {@link ChatbotTool} beans y las expone
 * en dos formatos:
 * <ul>
 *   <li>{@link #buildToolsPayload()} — formato Gemini (para el campo {@code tools} del payload)</li>
 *   <li>{@link #execute(String, JsonNode)} — ejecuta una tool por nombre y devuelve el resultado</li>
 * </ul>
 */
@Component
public class ChatbotToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatbotToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ChatbotTool> tools;

    public ChatbotToolRegistry(List<ChatbotTool> toolList) {
        this.tools = new LinkedHashMap<>();
        for (ChatbotTool tool : toolList) {
            this.tools.put(tool.getName(), tool);
            log.info("Chatbot tool registrada: {} — {}", tool.getName(), tool.getDescription());
        }
        log.info("Total tools disponibles: {}", tools.size());
    }

    /**
     * Construye el array {@code tools} en el formato que espera Gemini:
     * <pre>
     * [{ "functionDeclarations": [ { name, description, parameters }, ... ] }]
     * </pre>
     */
    public List<Map<String, Object>> buildToolsPayload() {
        ArrayNode functionDeclarations = MAPPER.createArrayNode();

        for (ChatbotTool tool : tools.values()) {
            ObjectNode declaration = MAPPER.createObjectNode();
            declaration.put("name", tool.getName());
            declaration.put("description", tool.getDescription());
            declaration.set("parameters", tool.getInputSchema());
            functionDeclarations.add(declaration);
        }

        Map<String, Object> functionDeclarationsWrapper = new LinkedHashMap<>();
        functionDeclarationsWrapper.put("functionDeclarations", functionDeclarations);

        return List.of(functionDeclarationsWrapper);
    }

    /**
     * Ejecuta una tool por nombre. Si la tool no existe, retorna un error JSON.
     */
    public JsonNode execute(String toolName, JsonNode args) {
        ChatbotTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("Tool desconocida solicitada por Gemini: {}", toolName);
            return MAPPER.createObjectNode().put("error", "Tool no encontrada: " + toolName);
        }
        try {
            return tool.execute(args);
        } catch (Exception ex) {
            log.error("Error ejecutando tool {}: {}", toolName, ex.getMessage(), ex);
            return MAPPER.createObjectNode().put("error", "Error ejecutando " + toolName + ": " + ex.getMessage());
        }
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }
}
