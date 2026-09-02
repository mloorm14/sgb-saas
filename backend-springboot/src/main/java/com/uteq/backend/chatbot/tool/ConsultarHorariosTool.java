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
 * Tool que consulta los horarios de apertura de la biblioteca.
 * Filtra la base de conocimiento por categoría HORARIOS.
 */
@Component
public class ConsultarHorariosTool implements ChatbotTool {

    private final BaseConocimientoRepository baseConocimientoRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultarHorariosTool(BaseConocimientoRepository baseConocimientoRepo) {
        this.baseConocimientoRepo = baseConocimientoRepo;
    }

    @Override
    public String getName() {
        return "consultar_horarios";
    }

    @Override
    public String getDescription() {
        return "Consulta los horarios de apertura de la biblioteca. "
                + "Incluye horarios de lunes a viernes, sábados y días especiales.";
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
        List<BaseConocimiento> horarios = baseConocimientoRepo.findByActivoTrue().stream()
                .filter(bc -> "HORARIOS".equalsIgnoreCase(bc.getCategoria()))
                .toList();

        ArrayNode horariosArray = mapper.createArrayNode();
        for (BaseConocimiento bc : horarios) {
            ObjectNode nodo = mapper.createObjectNode();
            nodo.put("pregunta", bc.getPreguntaEjemplo());
            nodo.put("respuesta", bc.getRespuesta());
            horariosArray.add(nodo);
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set("horarios", horariosArray);
        respuesta.put("total", horarios.size());
        return respuesta;
    }
}
