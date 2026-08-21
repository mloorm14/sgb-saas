package com.uteq.backend.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

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

        // Ubicación física (ej. "Estante A-12"). Opcional: un libro puede
        // no tener estantería asignada todavía, mismo criterio que resumen.
        @Size(max = 50, message = "La ubicación física no puede superar 50 caracteres")
        String ubicacionFisica,

        @Size(max = 1000)
        String portadaUrl,

        @NotNull(message = "La editorial es obligatoria")
        Integer editorialId,

        @NotNull(message = "El idioma es obligatorio")
        Integer idiomaId,

        @NotNull(message = "El estado es obligatorio")
        Integer estadoId,

        @NotNull
        @Min(0)
        Integer stockTotal,

        @NotNull
        @Min(0)
        Integer stockDisponible,

        // Módulo 9.1: asociación con categorias/autores existentes. null o
        // vacío es válido (un libro puede no tener categoría/autor
        // asignado todavía) -- por eso sin @NotNull/@NotEmpty, a
        // diferencia de editorialId/idiomaId/estadoId que sí son
        // obligatorios.
        Set<Integer> categoriaIds,

        Set<Integer> autorIds
) {}