package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfiguracionSistemaRequestDTO(
        @NotBlank(message = "El valor no puede estar vacío")
        @Size(max = 200, message = "El valor no puede superar los 200 caracteres")
        String valor
) {}
