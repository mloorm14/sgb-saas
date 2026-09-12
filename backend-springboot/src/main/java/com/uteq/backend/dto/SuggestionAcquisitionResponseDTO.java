package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SuggestionAcquisitionResponseDTO(
        @JsonProperty("usuarioId") Long id, Long userId, @JsonProperty("titulo") String title, @JsonProperty("autor") String author,
        String isbn,
        String justificacion, @JsonProperty("estado") String status, @JsonProperty("revisadoPor") Long revisadoBy, @JsonProperty("creadoEn") OffsetDateTime created
) {}
