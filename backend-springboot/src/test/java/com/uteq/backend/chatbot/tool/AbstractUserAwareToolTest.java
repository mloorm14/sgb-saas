package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests para la base de tools con usuario_id (CHAT-01).
 * Camino feliz + casos de error reales, sin Spring.
 */
class AbstractUserAwareToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Subclase mínima para ejercitar la base abstracta. */
    static class ToolTest extends AbstractUserAwareTool {
        @Override
        public String getName() {
            return "tool_prueba";
        }

        @Override
        public String getDescription() {
            return "Tool de prueba";
        }

        @Override
        public JsonNode execute(JsonNode args) {
            Long id = resolveUserId(args);
            if (id == null) return errorNode("Se requiere usuario_id");
            return MAPPER.createObjectNode().put("usuario_id", id);
        }
    }

    private final ToolTest tool = new ToolTest();

    private static ObjectNode argsWith(Object value) {
        ObjectNode args = MAPPER.createObjectNode();
        if (value instanceof Number n) args.put("usuario_id", n.longValue());
        return args;
    }

    @Test
    @DisplayName("schema exige usuario_id entero con descripcion de sesion")
    void schemaExigeUserId() {
        JsonNode schema = tool.getInputSchema();

        assertEquals("object", schema.path("type").asText());
        assertEquals("integer", schema.path("properties").path("usuario_id").path("type").asText());
        assertTrue(schema.path("properties").path("usuario_id").path("description").asText().contains("sesión"));
        assertEquals("usuario_id", schema.path("required").path(0).asText());
    }

    @Test
    @DisplayName("resuelve usuario_id valido desde los argumentos")
    void resuelveUserIdValid() {
        JsonNode result = tool.execute(argsWith(7));

        assertEquals(7, result.path("usuario_id").asLong());
    }

    @Test
    @DisplayName("retorna error cuando usuario_id es cero o falta")
    void errorCuandoUserIdInvalid() {
        assertEquals("Se requiere usuario_id", tool.execute(argsWith(0)).path("error").asText());
        assertEquals("Se requiere usuario_id", tool.execute(MAPPER.createObjectNode()).path("error").asText());
    }

    @Test
    @DisplayName("resolverUsuarioId retorna null con args nulos")
    void nullWithArgsNulos() {
        assertNull(tool.resolveUserId(null));
    }
}
