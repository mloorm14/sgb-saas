package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.service.BookService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que busca libros en el catálogo real por título, autor o tema.
 * Usa {@link BookService#sugerir(String)} que internamente consulta con
 * pg_trgm (similitud de texto) y retorna los 3 resultados más relevantes.
 */
@Component
public class SearchBookTool extends AbstractChatbotTool {

    private static final String PARAM_QUERY = "query";

    private final BookService bookService;

    public SearchBookTool(BookService bookService) {
        this.bookService = bookService;
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "buscar_libro";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Busca libros en el catálogo de la biblioteca por título, autor o tema. "
                + "Devuelve los resultados más relevantes con su disponibilidad actual.";
    }

    @Override
    /**
     * Retrieves input schema.
     *
     * @return json node with the resulting state after the operation
     */
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode queryProp = mapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Título, autor o tema a buscar (ej: 'Clean Code', 'machine learning')");
        properties.set(PARAM_QUERY, queryProp);

        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add(PARAM_QUERY);
        schema.set("required", required);

        return schema;
    }

    @Override
    /**
     * Procesa execute y devuelve el resultado calculado por el backend.
     *
     * @param args argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public JsonNode execute(JsonNode args) {
        String query = args.path(PARAM_QUERY).asText("");
        List<BookSuggestionDTO> results = bookService.sugerir(query);

        ArrayNode resultsArray = mapper.createArrayNode();
        for (BookSuggestionDTO book : results) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", book.id());
            node.put("titulo", book.title());
            node.put("disponible", Boolean.TRUE.equals(book.available()));
            resultsArray.add(node);
        }

        ObjectNode response = mapper.createObjectNode();
        response.set("resultados", resultsArray);
        response.put("total", results.size());
        response.put("query", query);
        return response;
    }
}
