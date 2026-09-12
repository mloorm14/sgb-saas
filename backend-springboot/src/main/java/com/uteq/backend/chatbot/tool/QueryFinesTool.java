package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.StatusFineRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Tool que consulta las multas pendientes de pago de un usuario.
 * Incluye saldo total adeudado y cantidad de multas.
 */
@Component
public class QueryFinesTool extends AbstractUserAwareTool {

    private final FineRepository fineRepo;
    private final StatusFineRepository statusFineRepo;

    public QueryFinesTool(FineRepository fineRepo, StatusFineRepository statusFineRepo) {
        this.fineRepo = fineRepo;
        this.statusFineRepo = statusFineRepo;
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "consultar_multas";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Consulta las multas pendientes de pago de un usuario de la biblioteca. "
                + "Devuelve el saldo total adeudado y la cantidad de multas pendientes.";
    }

    @Override
    /**
     * Procesa execute y devuelve el resultado calculado por el backend.
     *
     * @param args argumento recibido por la herramienta del chatbot para decidir y ejecutar la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public JsonNode execute(JsonNode args) {
        Long userId = resolveUserId(args);
        if (userId == null) {
            return errorNode("Se requiere usuario_id");
        }

        Integer statusPendingId = statusFineRepo.findByName("PENDIENTE")
                .map(e -> e.getId())
                .orElse(null);

        if (statusPendingId == null) {
            return errorNode("Estado PENDIENTE no encontrado en catálogo");
        }

        long quantity = fineRepo.countByUserIdAndStatusFineId(userId, statusPendingId);
        BigDecimal balanceTotal = fineRepo.sumBalanceByUserIdAndStatusFineId(userId, statusPendingId);

        ObjectNode response = mapper.createObjectNode();
        response.put(USUARIO_ID, userId);
        response.put("multas_pendientes", quantity);
        response.put("saldo_total_pendiente", balanceTotal != null ? balanceTotal.doubleValue() : 0.0);
        response.put("tiene_multas_pendientes", quantity > 0);
        return response;
    }
}
