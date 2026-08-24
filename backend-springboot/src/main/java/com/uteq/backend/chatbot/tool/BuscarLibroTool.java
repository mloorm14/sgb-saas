package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.service.LibroService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que busca libros en el catálogo real por título, autor o tema.
 * Usa {@link LibroService#sugerir(String)} que internamente consulta con
 * pg_trgm (similitud de texto) y retorna los 3 resultados más relevantes.
 */
@Component
public class BuscarLibroTool implements ChatbotTool {

    private final LibroService libroService;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuscarLibroTool(LibroService libroService) {
        this.libroService = libroService;
    }

    @Override
    public String getName() {
        return "buscar_libro";
    }

    @Override
    public String getDescription() {
        return "Busca libros en el catálogo de la biblioteca por título, autor o tema. "
                + "Devuelve los resultados más relevantes con su disponibilidad actual.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode queryProp = mapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Título, autor o tema a buscar (ej: 'Clean Code', 'machine learning')");
        properties.set("query", queryProp);

        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("query");
        schema.set("required", required);

        return schema;
    }

    @Override
    public JsonNode execute(JsonNode args) {
        String query = args.path("query").asText("");
        List<LibroSugerenciaDTO> resultados = libroService.sugerir(query);

        ArrayNode resultadosArray = mapper.createArrayNode();
        for (LibroSugerenciaDTO libro : resultados) {
            ObjectNode nodo = mapper.createObjectNode();
            nodo.put("id", libro.id());
            nodo.put("titulo", libro.titulo());
            nodo.put("disponible", Boolean.TRUE.equals(libro.disponible()));
            resultadosArray.add(nodo);
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set("resultados", resultadosArray);
        respuesta.put("total", resultados.size());
        respuesta.put("query", query);
        return respuesta;
    }
}
