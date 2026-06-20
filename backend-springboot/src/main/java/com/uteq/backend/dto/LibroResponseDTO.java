package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record LibroResponseDTO(

        Long id,
        String titulo,
        String isbn,
        String resumen,
        String portadaUrl,
        Integer anioPublicacion,
        String editorial,
        String idioma,
        String estado,
        Integer stockTotal,
        Integer stockDisponible,
        Boolean activo,
        OffsetDateTime creadoEn
) {}