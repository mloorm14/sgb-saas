package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Fila del historial de reservaciones de un usuario
 * (GET /api/v1/reservaciones/gestion/historial-reservaciones?usuarioId=).
 *
 * El frontend lo pinta como tarjetas con badge de estado coloreado.
 * El título del libro viene resuelto para evitar llamadas N+1.
 */
public record HistoryReservationDTO( @JsonProperty("reservacionId") Long reservationId, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("estadoNombre") String statusName, @JsonProperty("estadoId") Integer statusId, @JsonProperty("fechaReserva") OffsetDateTime dateReservation,
        @JsonProperty("fechaLimiteRetiro") OffsetDateTime dateLimitPickup
) {}
