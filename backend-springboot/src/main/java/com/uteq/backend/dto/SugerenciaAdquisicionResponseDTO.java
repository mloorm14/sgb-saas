package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record SugerenciaAdquisicionResponseDTO(
        Long id,
        Long usuarioId,
        String titulo,
        String autor,
        String isbn,
        String justificacion,
        String estado,
        Long revisadoPor,
        OffsetDateTime creadoEn
) {}
