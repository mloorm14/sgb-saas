package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.ChatbotTool;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.ReservacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tool que consulta las reservas vigentes de un usuario.
 * "Vigente" = estado PENDIENTE o LISTA_PARA_RETIRO.
 * El usuario_id se inyecta automáticamente desde el orchestrator.
 */
@Component
public class ConsultarReservacionesTool implements ChatbotTool {

    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final LibroRepository libroRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultarReservacionesTool(ReservacionRepository reservacionRepo,
                                      EstadoReservacionRepository estadoReservacionRepo,
                                      LibroRepository libroRepo) {
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.libroRepo = libroRepo;
    }

    @Override
    public String getName() {
        return "consultar_reservaciones";
    }

    @Override
    public String getDescription() {
        return "Consulta las reservas vigentes (PENDIENTE o LISTA_PARA_RETIRO) de un usuario de la biblioteca. "
                + "Devuelve el listado con libro, fechas y estado.";
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

        // Resolver IDs de estados vigentes
        Integer estadoPendienteId = estadoReservacionRepo.findByNombre("PENDIENTE")
                .map(EstadoReservacion::getId)
                .orElse(null);
        Integer estadoListaParaRetiroId = estadoReservacionRepo.findByNombre("LISTA_PARA_RETIRO")
                .map(EstadoReservacion::getId)
                .orElse(null);

        if (estadoPendienteId == null && estadoListaParaRetiroId == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Estados de reserva vigentes no encontrados en catálogo");
            return error;
        }

        Page<com.uteq.backend.entity.Reservacion> pagina = reservacionRepo.findByUsuarioId(
                usuarioId, PageRequest.of(0, 20));

        List<com.uteq.backend.entity.Reservacion> vigentes = pagina.getContent().stream()
                .filter(r -> (estadoPendienteId != null && estadoPendienteId.equals(r.getEstadoReservacionId()))
                        || (estadoListaParaRetiroId != null && estadoListaParaRetiroId.equals(r.getEstadoReservacionId())))
                .toList();

        ArrayNode reservasArray = mapper.createArrayNode();
        for (com.uteq.backend.entity.Reservacion r : vigentes) {
            Optional<Libro> libroOpt = libroRepo.findById(r.getLibroId());
            Optional<EstadoReservacion> estadoOpt = estadoReservacionRepo.findById(r.getEstadoReservacionId());

            if (libroOpt.isEmpty() || estadoOpt.isEmpty()) {
                continue; // saltar si falta dato relacionado
            }

            Libro libro = libroOpt.get();
            EstadoReservacion estado = estadoOpt.get();

            ObjectNode nodo = mapper.createObjectNode();
            nodo.put("reservacion_id", r.getId());
            nodo.put("libro_id", libro.getId());
            nodo.put("titulo", libro.getTitulo());
            nodo.put("isbn", libro.getIsbn());
            nodo.put("fecha_reserva", r.getFechaReserva() != null ? r.getFechaReserva().toString() : null);
            nodo.put("fecha_limite_retiro", r.getFechaLimiteRetiro() != null ? r.getFechaLimiteRetiro().toString() : null);
            nodo.put("estado", estado.getNombre());
            reservasArray.add(nodo);
        }

        ObjectNode respuesta = mapper.createObjectNode();
        respuesta.set("reservas_vigentes", reservasArray);
        respuesta.put("total", vigentes.size());
        respuesta.put("usuario_id", usuarioId);
        return respuesta;
    }
}