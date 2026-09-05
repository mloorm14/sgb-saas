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
class AbstractUsuarioAwareToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Subclase mínima para ejercitar la base abstracta. */
    static class ToolDePrueba extends AbstractUsuarioAwareTool {
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
            Long id = resolverUsuarioId(args);
            if (id == null) return errorNode("Se requiere usuario_id");
            return MAPPER.createObjectNode().put("usuario_id", id);
        }
    }

    private final ToolDePrueba tool = new ToolDePrueba();

    private static ObjectNode argsCon(Object valor) {
        ObjectNode args = MAPPER.createObjectNode();
        if (valor instanceof Number n) args.put("usuario_id", n.longValue());
        return args;
    }

    @Test
    @DisplayName("schema exige usuario_id entero con descripcion de sesion")
    void schemaExigeUsuarioId() {
        JsonNode schema = tool.getInputSchema();

        assertEquals("object", schema.path("type").asText());
        assertEquals("integer", schema.path("properties").path("usuario_id").path("type").asText());
        assertTrue(schema.path("properties").path("usuario_id").path("description").asText().contains("sesión"));
        assertEquals("usuario_id", schema.path("required").path(0).asText());
    }

    @Test
    @DisplayName("resuelve usuario_id valido desde los argumentos")
    void resuelveUsuarioIdValido() {
        JsonNode resultado = tool.execute(argsCon(7));

        assertEquals(7, resultado.path("usuario_id").asLong());
    }

    @Test
    @DisplayName("retorna error cuando usuario_id es cero o falta")
    void errorCuandoUsuarioIdInvalido() {
        assertEquals("Se requiere usuario_id", tool.execute(argsCon(0)).path("error").asText());
        assertEquals("Se requiere usuario_id", tool.execute(MAPPER.createObjectNode()).path("error").asText());
    }

    @Test
    @DisplayName("resolverUsuarioId retorna null con args nulos")
    void nullConArgsNulos() {
        assertNull(tool.resolverUsuarioId(null));
    }
}
