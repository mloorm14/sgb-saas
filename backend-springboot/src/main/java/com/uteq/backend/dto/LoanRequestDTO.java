package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Crea un préstamo (usuarioId o credencialQrToken, exactamente uno; reservacionId opcional la vincula y marca RETIRADA).
public record LoanRequestDTO( @JsonProperty("usuarioId") Long userId, @JsonProperty("credencialQrToken") UUID credentialQrToken,

        @NotNull(message = "El libro es obligatorio") @JsonProperty("libroId") Long bookId,

        @NotNull(message = "Los días de préstamo son obligatorios")
        @Min(value = 1, message = "Los días de préstamo deben ser al menos 1") @JsonProperty("diasPrestamo") Integer daysLoan,

        @JsonProperty("reservacionId") Long reservationId
) {}
