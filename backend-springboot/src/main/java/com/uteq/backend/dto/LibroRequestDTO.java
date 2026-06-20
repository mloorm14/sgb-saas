package com.uteq.backend.dto;

import jakarta.validation.constraints.*;

public record LibroRequestDTO(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @NotBlank(message = "El ISBN es obligatorio")
        @Pattern(regexp = "^[0-9\\-]{10,17}$", message = "ISBN inválido")
        @Size(max = 13, message = "El ISBN no puede superar 13 caracteres")
        String isbn,

        @NotNull(message = "El año de publicación es obligatorio")
        @Min(value = 1000, message = "Año inválido")
        @Max(value = 2100, message = "Año inválido")
        Integer anioPublicacion,

        String resumen,

        @Size(max = 1000)
        String portadaUrl,

        @NotNull(message = "La editorial es obligatoria")
        Long editorialId,

        @NotNull(message = "El idioma es obligatorio")
        Long idiomaId,

        @NotNull(message = "El estado es obligatorio")
        Long estadoId,

        @NotNull
        @Min(0)
        Integer stockTotal,

        @NotNull
        @Min(0)
        Integer stockDisponible
) {}