package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record UserReasonChangeResponseDTO(
        @JsonProperty("ejecutadoPor") Long id, @JsonProperty("tipoCambio") String typeChange, @JsonProperty("estadoAnterior") Integer statusAnterior, @JsonProperty("estadoNuevo") Integer statusFresh, @JsonProperty("motivo") String reason, Long executedBy, @JsonProperty("creadoEn") OffsetDateTime created) {
}
