package com.uteq.backend.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record LibroRequestDTO(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @NotBlank(message = "El ISBN es obligatorio")
        @Pattern(regexp = "^[0-9]{10,13}$", message = "ISBN debe tener 10 a 13 dígitos numéricos")
        @Size(min = 10, max = 13, message = "El ISBN debe tener entre 10 y 13 caracteres")
        String isbn,

        @NotNull(message = "El año de publicación es obligatorio")
        @Min(value = 1950, message = "El año no puede ser menor a 1950")
        Integer anioPublicacion,

        @Min(value = 1, message = "El número de páginas debe ser mayor a 0")
        @Max(value = 4100, message = "El número de páginas no puede superar 4100")
        Integer numeroPaginas,

        @Digits(integer = 3, fraction = 2, message = "Precio base inválido (máx 250.00 con 2 decimales)")
        @DecimalMin(value = "0.00", message = "El precio base debe ser mayor o igual a 0")
        @DecimalMax(value = "250.00", message = "El precio base no puede superar 250.00")
        java.math.BigDecimal precioBase,

        @Size(max = 2000, message = "El resumen no puede superar 2000 caracteres")
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

        Set<Integer> autorIds,

        // Proveedor opcional — null o ausente = S/P (Sin proveedor).
        Integer proveedorId
) {}