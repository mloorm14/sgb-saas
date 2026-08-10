package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SugerenciaAdquisicionRequestDTO(

        @NotBlank(message = "El título sugerido es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @Size(max = 150, message = "El autor no puede superar 150 caracteres")
        String autor,

        @Pattern(regexp = "^[0-9\\-]{10,17}$", message = "ISBN inválido")
        @Size(max = 13, message = "El ISBN no puede superar 13 caracteres")
        String isbn,

        @Size(max = 1000, message = "La justificación no puede superar 1000 caracteres")
        String justificacion
) {}
