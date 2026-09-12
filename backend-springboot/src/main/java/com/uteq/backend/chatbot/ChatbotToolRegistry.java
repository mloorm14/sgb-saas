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
 *   <li>{@link #buildToolsPayload} — formato Gemini (para el campo {@code tools} del payload)</li>
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
     * Procesa build tools payload y devuelve el resultado calculado por el backend.
     *
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Procesa execute y devuelve el resultado calculado por el backend.
     *
     * @param toolName argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @param args argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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

    /**
     * Verifica contains y devuelve el resultado de la comprobacion.
     *
     * @param toolName argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return true cuando la comprobacion se cumple; false en caso contrario
     */

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * Consulta get tool schema usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param toolName argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public JsonNode getToolSchema(String toolName) {
        ChatbotTool tool = tools.get(toolName);
        if (tool == null) {
            return MAPPER.createObjectNode();
        }
        return tool.getInputSchema();
    }

    /**
     * Procesa requires user id y devuelve el resultado calculado por el backend.
     *
     * @param toolName argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return true cuando la comprobacion se cumple; false en caso contrario
     */
    public boolean requiresUserId(String toolName) {
        JsonNode schema = getToolSchema(toolName);
        JsonNode required = schema.path("required");
        if (!required.isArray()) {
            return false;
        }
        for (JsonNode req : required) {
            if ("usuario_id".equals(req.asText())) {
                return true;
            }
        }
        return false;
    }
}
