package com.uteq.backend.dto;

// Respuesta de GET /api/v1/libros/lookup-isbn?isbn= (Google Books).
// anioPublicacion puede ser null si Google Books no trae fecha; la
// portada NO viaja acá (se descarga aparte por /lookup-isbn/portada).
public record LibroIsbnLookupDTO(
        String titulo,
        String autor,
        String resumen,
        Integer anioPublicacion,
        Boolean portadaDisponible
) {}