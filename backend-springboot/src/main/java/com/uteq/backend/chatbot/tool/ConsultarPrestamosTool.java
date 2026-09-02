package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta los préstamos activos de un usuario específico.
 * Usa el repository directamente (sin Authentication) porque la tool ya
 * fue invocada en contexto autenticado (ChatbotOrchestrator validó el usuario).
 */
@Component
public class ConsultarPrestamosTool implements ChatbotTool {

    private final PrestamoRepository prestamoRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultarPrestamosTool(PrestamoRepository prestamoRepo) {
        this.prestamoRepo = prestamoRepo;
    }

    @Override
    public String getName() {
        return "consultar_prestamos";
    }

    @Override
    public String getDescription() {
        return "Consulta los préstamos activos (no devueltos) de un usuario de la biblioteca. "
                + "Devuelve títulos, ISBNs, fechas de préstamo y devolución estimada.";
    }

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

    @Override
    public JsonNode execute(JsonNode args) {
        Long usuarioId = args.path("usuario_id").asLong(0);
        if (usuarioId == 0) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Se requiere usuario_id");
            return error;
        }

        List<PrestamoActivoProjection> prestamos = prestamoRepo.findActivosByUsuarioId(usuarioId);

        ArrayNode prestamosArray = mapper.createArrayNode();
        for (PrestamoActivoProjection p : prestamos) {
            ObjectNode nodo = mapper.createObjectNode();
            nodo.put("prestamo_id", p.getPrestamoId());
            nodo.put("titulo", p.getLibroTitulo());
            nodo.put("isbn", p.getLibroIsbn());
            nodo.put("fecha_prestamo", p.getFechaPrestamo() != null ? p.getFechaPrestamo().toString() : null);
            nodo.put("fecha_devolucion_estimada", p.getFechaDevolucionEstimada() != null ? p.getFechaDevolucionEstimada().toString() : null);
            nodo.put("dias_restantes", p.getDiasRestantes());
            nodo.put("estado", p.getEstadoNombre());
            prestamosArray.add(nodo);
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set("prestamos_activos", prestamosArray);
        respuesta.put("total", prestamos.size());
        respuesta.put("usuario_id", usuarioId);
        return respuesta;
    }
}
