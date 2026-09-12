package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FineResponseDTO(
        @JsonProperty("prestamoId") Long id, Long loanId, @JsonProperty("monto") BigDecimal amount, @JsonProperty("estadoMultaId") Integer statusFineId, @JsonProperty("fechaGenerada") OffsetDateTime dateGenerated, @JsonProperty("fechaPagada") OffsetDateTime datePaid, @JsonProperty("observaciones") String observations
) {}