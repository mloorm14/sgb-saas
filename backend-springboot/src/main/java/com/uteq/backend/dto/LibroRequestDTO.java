package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LibroRequestDTO(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar 200 caracteres")
        String titulo,

        @NotBlank(message = "El ISBN es obligatorio")
        @Pattern(regexp = "^[0-9\\-]{10,17}$", message = "ISBN inválido")
        String isbn,

        @Size(max = 300, message = "El autor no puede superar 300 caracteres")
        String autor,

        Integer anioPublicacion,

        Long editorialId,

        Long idiomaId,

        Long estadoId
) {}