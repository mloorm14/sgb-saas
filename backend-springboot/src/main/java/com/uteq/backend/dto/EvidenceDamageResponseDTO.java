package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record EvidenceDamageResponseDTO(
        Long id, @JsonProperty("registroDanoId") Long registrationDamageId, @JsonProperty("archivoNombre") String fileName, @JsonProperty("archivoTipo") String fileType, @JsonProperty("subidoEn") OffsetDateTime subido
) {}
