package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record LibroResponseDTO(

        Long id,
        String titulo,
        String isbn,
        String resumen,
        String portadaUrl,
        Integer anioPublicacion,
        Integer editorialId,
        String editorial,
        Integer idiomaId,
        String idioma,
        Integer estadoId,
        String estado,
        Integer stockTotal,
        Integer stockDisponible,
        Boolean activo,
        OffsetDateTime creadoEn
) {}
