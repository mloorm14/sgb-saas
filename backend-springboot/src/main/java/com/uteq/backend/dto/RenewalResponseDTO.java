package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RenewalResponseDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("nuevaFechaDevolucionEstimada") OffsetDateTime freshDateLoanReturnEstimada, @JsonProperty("renovacionesRealizadas") Short renewalsRealizadas,
        @JsonProperty("renovacionesRestantes") Short renewalsRestantes
) {}
