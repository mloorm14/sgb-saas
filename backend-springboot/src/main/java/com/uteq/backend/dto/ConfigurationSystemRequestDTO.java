package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigurationSystemRequestDTO(
        @Size(max = 200, message = "El valor no puede superar los 200 caracteres") @JsonProperty("valor") String value
) {}
