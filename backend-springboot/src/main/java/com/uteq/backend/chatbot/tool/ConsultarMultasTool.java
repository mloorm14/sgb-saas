package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Tool que consulta las multas pendientes de pago de un usuario.
 * Incluye saldo total adeudado y cantidad de multas.
 */
@Component
public class ConsultarMultasTool implements ChatbotTool {

    private final MultaRepository multaRepo;
    private final EstadoMultaRepository estadoMultaRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultarMultasTool(MultaRepository multaRepo, EstadoMultaRepository estadoMultaRepo) {
        this.multaRepo = multaRepo;
        this.estadoMultaRepo = estadoMultaRepo;
    }

    @Override
    public String getName() {
        return "consultar_multas";
    }

    @Override
    public String getDescription() {
        return "Consulta las multas pendientes de pago de un usuario de la biblioteca. "
                + "Devuelve el saldo total adeudado y la cantidad de multas pendientes.";
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

        Integer estadoPendienteId = estadoMultaRepo.findByNombre("PENDIENTE")
                .map(e -> e.getId())
                .orElse(null);

        if (estadoPendienteId == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Estado PENDIENTE no encontrado en catálogo");
            return error;
        }

        long cantidad = multaRepo.countByUsuarioIdAndEstadoMultaId(usuarioId, estadoPendienteId);
        BigDecimal saldoTotal = multaRepo.sumSaldoByUsuarioIdAndEstadoMultaId(usuarioId, estadoPendienteId);

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.put("usuario_id", usuarioId);
        respuesta.put("multas_pendientes", cantidad);
        respuesta.put("saldo_total_pendiente", saldoTotal != null ? saldoTotal.doubleValue() : 0.0);
        respuesta.put("tiene_multas_pendientes", cantidad > 0);
        return respuesta;
    }
}
