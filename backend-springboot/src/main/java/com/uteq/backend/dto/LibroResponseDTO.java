package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record LibroResponseDTO(

        Long id,
        String titulo,
        String isbn,
        String autor,
        Integer anioPublicacion,
        String editorial,
        String idioma,
        String estado,
        Boolean activo,
        OffsetDateTime creadoEn
) {}