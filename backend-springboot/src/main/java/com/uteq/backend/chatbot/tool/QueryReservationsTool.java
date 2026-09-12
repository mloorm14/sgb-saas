package com.uteq.backend.chatbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Book;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.ReservationRepository;
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
public class QueryReservationsTool extends AbstractUserAwareTool {

    private final ReservationRepository reservationRepo;
    private final StatusReservationRepository statusReservationRepo;
    private final BookRepository bookRepo;

    public QueryReservationsTool(ReservationRepository reservationRepo,
                                      StatusReservationRepository statusReservationRepo,
                                      BookRepository bookRepo) {
        this.reservationRepo = reservationRepo;
        this.statusReservationRepo = statusReservationRepo;
        this.bookRepo = bookRepo;
    }

    @Override
    /**
     * Retrieves name.
     *
     * @return resulting text payload
     */
    public String getName() {
        return "consultar_reservaciones";
    }

    @Override
    /**
     * Retrieves scription.
     *
     * @return resulting text payload
     */
    public String getDescription() {
        return "Consulta las reservas vigentes (PENDIENTE o LISTA_PARA_RETIRO) de un usuario de la biblioteca. "
                + "Devuelve el listado con libro, fechas y estado.";
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

        // Resolver IDs de estados vigentes
        Integer statusPendingId = statusReservationRepo.findByName("PENDIENTE")
                .map(StatusReservation::getId)
                .orElse(null);
        Integer statusListaForPickupId = statusReservationRepo.findByName("LISTA_PARA_RETIRO")
                .map(StatusReservation::getId)
                .orElse(null);

        if (statusPendingId == null && statusListaForPickupId == null) {
            return errorNode("Estados de reserva vigentes no encontrados en catálogo");
        }

        Page<com.uteq.backend.entity.Reservation> page = reservationRepo.findByUserId(
                userId, PageRequest.of(0, 20));

        List<com.uteq.backend.entity.Reservation> vigentes = page.getContent().stream()
                .filter(r -> (statusPendingId != null && statusPendingId.equals(r.getStatusReservationId()))
                        || (statusListaForPickupId != null && statusListaForPickupId.equals(r.getStatusReservationId())))
                .toList();

        ArrayNode reservationsArray = mapper.createArrayNode();
        for (com.uteq.backend.entity.Reservation r : vigentes) {
            Optional<Book> bookOpt = bookRepo.findById(r.getBookId());
            Optional<StatusReservation> statusOpt = statusReservationRepo.findById(r.getStatusReservationId());

            if (bookOpt.isEmpty() || statusOpt.isEmpty()) {
                continue; // saltar si falta dato relacionado
            }

            Book book = bookOpt.get();
            StatusReservation status = statusOpt.get();

            ObjectNode node = mapper.createObjectNode();
            node.put("reservacion_id", r.getId());
            node.put("libro_id", book.getId());
            node.put("titulo", book.getTitle());
            node.put("isbn", book.getIsbn());
            node.put("fecha_reserva", r.getDateReservation() != null ? r.getDateReservation().toString() : null);
            node.put("fecha_limite_retiro", r.getDateLimitPickup() != null ? r.getDateLimitPickup().toString() : null);
            node.put("estado", status.getName());
            reservationsArray.add(node);
        }

        ObjectNode response = mapper.createObjectNode();
        response.set("reservas_vigentes", reservationsArray);
        response.put("total", vigentes.size());
        response.put(USUARIO_ID, userId);
        return response;
    }
}
