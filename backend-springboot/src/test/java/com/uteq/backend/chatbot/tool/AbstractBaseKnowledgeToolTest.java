package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests para el Template Method de base de conocimiento (CHAT-02).
 * Mock del repository, sin Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AbstractBaseKnowledgeToolTest {

    @Mock
    private BaseKnowledgeRepository repo;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static class ToolSchedulesTest extends AbstractKnowledgeBaseTool {
        ToolSchedulesTest(BaseKnowledgeRepository repo) {
            super(repo);
        }

        @Override
        public String getName() {
            return "horarios_prueba";
        }

        @Override
        public String getDescription() {
            return "prueba";
        }

        @Override
        protected List<String> getCategories() {
            return List.of("HORARIOS");
        }

        @Override
        protected String getResponseKey() {
            return "horarios";
        }

        @Override
        protected com.fasterxml.jackson.databind.node.ObjectNode mapearInput(KnowledgeBase bc) {
            return MAPPER.createObjectNode().put("respuesta", bc.getResponse());
        }
    }

    private static KnowledgeBase input(String category, String response) {
        KnowledgeBase bc = new KnowledgeBase();
        bc.setCategory(category);
        bc.setQuestionExample("¿pregunta?");
        bc.setResponse(response);
        bc.setActive(true);
        return bc;
    }

    @Test
    @DisplayName("schema vacio sin parametros")
    void schemaVacio() {
        JsonNode schema = new ToolSchedulesTest(repo).getInputSchema();

        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.path("properties").isEmpty());
    }

    @Test
    @DisplayName("filtra por categoria sin importar mayusculas y mapea entradas")
    void filtraByCategory() {
        when(repo.findByActiveTrue()).thenReturn(List.of(
                input("HORARIOS", "Lun-Vie 8-18"),
                input("horarios", "Sáb 9-13"),
                input("POLITICAS", "Otra cosa")));

        JsonNode result = new ToolSchedulesTest(repo).execute(MAPPER.createObjectNode());

        assertEquals(2, result.path("total").asInt());
        assertEquals("Lun-Vie 8-18", result.path("horarios").path(0).path("respuesta").asText());
        assertEquals("Sáb 9-13", result.path("horarios").path(1).path("respuesta").asText());
    }

    @Test
    @DisplayName("ignora entradas con categoria nula y retorna total cero si no hay coincidencias")
    void ignoraCategoryNula() {
        KnowledgeBase withoutCategory = new KnowledgeBase();
        withoutCategory.setResponse("?");
        when(repo.findByActiveTrue()).thenReturn(List.of(withoutCategory));

        JsonNode result = new ToolSchedulesTest(repo).execute(MAPPER.createObjectNode());

        assertEquals(0, result.path("total").asInt());
        assertTrue(result.path("horarios").isEmpty());
    }
}
