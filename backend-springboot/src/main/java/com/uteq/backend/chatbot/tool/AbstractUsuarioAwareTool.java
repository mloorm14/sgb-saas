package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Base para tools que operan sobre un usuario (parámetro
 * {@code usuario_id} resuelto desde la sesión autenticada).
 * Centraliza el schema de entrada y la validación del ID.
 */
public abstract class AbstractUsuarioAwareTool extends AbstractChatbotTool {

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode usuarioIdProp = mapper.createObjectNode();
        usuarioIdProp.put("type", "integer");
        usuarioIdProp.put("description", "ID del usuario (se resuelve automáticamente desde la sesión autenticada)");
        properties.set("usuario_id", usuarioIdProp);

        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("usuario_id");
        schema.set("required", required);

        return schema;
    }

    /**
     * Extrae y valida {@code usuario_id} de los argumentos.
     * @return el ID, o {@code null} si falta o es inválido.
     */
    protected Long resolverUsuarioId(JsonNode args) {
        if (args == null) return null;
        long id = args.path("usuario_id").asLong(0);
        return id == 0 ? null : id;
    }
}
