package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.repository.BaseConocimientoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta información sobre políticas de la biblioteca:
 * préstamo, devolución, sanciones, etc.
 * Filtra la base de conocimiento por categoría POLITICAS.
 */
@Component
public class InfoPoliticasTool implements ChatbotTool {

    private final BaseConocimientoRepository baseConocimientoRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public InfoPoliticasTool(BaseConocimientoRepository baseConocimientoRepo) {
        this.baseConocimientoRepo = baseConocimientoRepo;
    }

    @Override
    public String getName() {
        return "info_politicas";
    }

    @Override
    public String getDescription() {
        return "Consulta información sobre las políticas de la biblioteca: "
                + "préstamo, devolución, renovaciones, sanciones, multas y reglas generales.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode args) {
        List<BaseConocimiento> politicas = baseConocimientoRepo.findByActivoTrue().stream()
                .filter(bc -> "POLITICAS".equalsIgnoreCase(bc.getCategoria())
                        || "MULTAS".equalsIgnoreCase(bc.getCategoria()))
                .toList();

        ArrayNode politicasArray = mapper.createArrayNode();
        for (BaseConocimiento bc : politicas) {
            ObjectNode nodo = mapper.createObjectNode();
            nodo.put("categoria", bc.getCategoria());
            nodo.put("pregunta", bc.getPreguntaEjemplo());
            nodo.put("respuesta", bc.getRespuesta());
            politicasArray.add(nodo);
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set("politicas", politicasArray);
        respuesta.put("total", politicas.size());
        return respuesta;
    }
}
