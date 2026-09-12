package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ReservationTodayResponseDTO( @JsonProperty("reservacionId") Long reservationId, @JsonProperty("usuarioNombre") String userName, @JsonProperty("usuarioCorreo") String userEmail, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("libroIsbn") String bookIsbn, @JsonProperty("estadoNombre") String statusName, @JsonProperty("fechaLimiteRetiro") OffsetDateTime dateLimitPickup
) {}
