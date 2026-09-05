package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class ConsultarPrestamosTool extends AbstractUsuarioAwareTool {

    private final PrestamoRepository prestamoRepo;

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
    public JsonNode execute(JsonNode args) {
        Long usuarioId = resolverUsuarioId(args);
        if (usuarioId == null) {
            return errorNode("Se requiere usuario_id");
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
