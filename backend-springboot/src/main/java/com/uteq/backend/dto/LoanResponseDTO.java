package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record LoanResponseDTO(
        Long id, @JsonProperty("usuarioId") Long userId, @JsonProperty("libroId") Long bookId, @JsonProperty("bibliotecarioId") Long librarianId, @JsonProperty("reservacionId") Long reservationId, @JsonProperty("fechaPrestamo") OffsetDateTime dateLoan, @JsonProperty("fechaDevolucionEstimada") OffsetDateTime dateLoanReturnEstimada, @JsonProperty("fechaDevolucionReal") OffsetDateTime dateLoanReturnReal, @JsonProperty("renovacionesRealizadas") Short renewalsRealizadas, @JsonProperty("estadoPrestamoId") Integer statusLoanId
) {}
