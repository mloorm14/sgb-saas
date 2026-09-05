package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Tool que consulta las multas pendientes de pago de un usuario.
 * Incluye saldo total adeudado y cantidad de multas.
 */
@Component
public class ConsultarMultasTool extends AbstractUsuarioAwareTool {

    private final MultaRepository multaRepo;
    private final EstadoMultaRepository estadoMultaRepo;

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
    public JsonNode execute(JsonNode args) {
        Long usuarioId = resolverUsuarioId(args);
        if (usuarioId == null) {
            return errorNode("Se requiere usuario_id");
        }

        Integer estadoPendienteId = estadoMultaRepo.findByNombre("PENDIENTE")
                .map(e -> e.getId())
                .orElse(null);

        if (estadoPendienteId == null) {
            return errorNode("Estado PENDIENTE no encontrado en catálogo");
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
