package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.repository.BaseConocimientoRepository;
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
class AbstractBaseConocimientoToolTest {

    @Mock
    private BaseConocimientoRepository repo;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static class ToolHorariosPrueba extends AbstractBaseConocimientoTool {
        ToolHorariosPrueba(BaseConocimientoRepository repo) {
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
        protected List<String> getCategorias() {
            return List.of("HORARIOS");
        }

        @Override
        protected String getRespuestaKey() {
            return "horarios";
        }

        @Override
        protected com.fasterxml.jackson.databind.node.ObjectNode mapearEntrada(BaseConocimiento bc) {
            return MAPPER.createObjectNode().put("respuesta", bc.getRespuesta());
        }
    }

    private static BaseConocimiento entrada(String categoria, String respuesta) {
        BaseConocimiento bc = new BaseConocimiento();
        bc.setCategoria(categoria);
        bc.setPreguntaEjemplo("¿pregunta?");
        bc.setRespuesta(respuesta);
        bc.setActivo(true);
        return bc;
    }

    @Test
    @DisplayName("schema vacio sin parametros")
    void schemaVacio() {
        JsonNode schema = new ToolHorariosPrueba(repo).getInputSchema();

        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.path("properties").isEmpty());
    }

    @Test
    @DisplayName("filtra por categoria sin importar mayusculas y mapea entradas")
    void filtraPorCategoria() {
        when(repo.findByActivoTrue()).thenReturn(List.of(
                entrada("HORARIOS", "Lun-Vie 8-18"),
                entrada("horarios", "Sáb 9-13"),
                entrada("POLITICAS", "Otra cosa")));

        JsonNode resultado = new ToolHorariosPrueba(repo).execute(MAPPER.createObjectNode());

        assertEquals(2, resultado.path("total").asInt());
        assertEquals("Lun-Vie 8-18", resultado.path("horarios").path(0).path("respuesta").asText());
        assertEquals("Sáb 9-13", resultado.path("horarios").path(1).path("respuesta").asText());
    }

    @Test
    @DisplayName("ignora entradas con categoria nula y retorna total cero si no hay coincidencias")
    void ignoraCategoriaNula() {
        BaseConocimiento sinCategoria = new BaseConocimiento();
        sinCategoria.setRespuesta("?");
        when(repo.findByActivoTrue()).thenReturn(List.of(sinCategoria));

        JsonNode resultado = new ToolHorariosPrueba(repo).execute(MAPPER.createObjectNode());

        assertEquals(0, resultado.path("total").asInt());
        assertTrue(resultado.path("horarios").isEmpty());
    }
}
