package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.projection.LoanActiveProjection;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que consulta los préstamos activos de un usuario específico.
 * Usa el repository directamente (sin Authentication) porque la tool ya
 * fue invocada en contexto autenticado (ChatbotOrchestrator validó el usuario).
 */
@Component
public class QueryLoansTool extends AbstractUserAwareTool {

    private final LoanRepository loanRepo;

    public QueryLoansTool(LoanRepository loanRepo) {
        this.loanRepo = loanRepo;
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "consultar_prestamos";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Consulta los préstamos activos (no devueltos) de un usuario de la biblioteca. "
                + "Devuelve títulos, ISBNs, fechas de préstamo y devolución estimada.";
    }

    @Override
    /**
     * Executes JSON payload Node.
     *
     * @param args JSON payload Node used to scope this JSON payload Node
     * @return JSON payload Node reflecting the state after the operation
     */
    public JsonNode execute(JsonNode args) {
        Long userId = resolveUserId(args);
        if (userId == null) {
            return errorNode("Se requiere usuario_id");
        }

        List<LoanActiveProjection> loans = loanRepo.findActivesByUserId(userId);

        ArrayNode loansArray = mapper.createArrayNode();
        for (LoanActiveProjection p : loans) {
            ObjectNode node = mapper.createObjectNode();
            node.put("prestamo_id", p.getLoanId());
            node.put("titulo", p.getBookTitle());
            node.put("isbn", p.getBookIsbn());
            node.put("fecha_prestamo", p.getDateLoan() != null ? p.getDateLoan().toString() : null);
            node.put("fecha_devolucion_estimada", p.getDateLoanReturnEstimada() != null ? p.getDateLoanReturnEstimada().toString() : null);
            node.put("dias_restantes", p.getDaysRestantes());
            node.put("estado", p.getStatusName());
            loansArray.add(node);
        }

        ObjectNode response = mapper.createObjectNode();
        response.set("prestamos_activos", loansArray);
        response.put("total", loans.size());
        response.put(USUARIO_ID, userId);
        return response;
    }
}
