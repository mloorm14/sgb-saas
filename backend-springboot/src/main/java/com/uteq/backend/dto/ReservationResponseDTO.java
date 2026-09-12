package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ReservationResponseDTO(
        Long id, @JsonProperty("usuarioId") Long userId, @JsonProperty("libroId") Long bookId, @JsonProperty("estadoReservacionId") Integer statusReservationId, @JsonProperty("fechaReserva") OffsetDateTime dateReservation,
        @JsonProperty("fechaLimiteRetiro") OffsetDateTime dateLimitPickup
) {}
