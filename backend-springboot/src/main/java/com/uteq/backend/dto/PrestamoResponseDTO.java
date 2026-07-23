package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record PrestamoResponseDTO(
        Long id,
        Long usuarioId,
        Long libroId,
        Long bibliotecarioId,
        Long reservacionId,
        OffsetDateTime fechaPrestamo,
        OffsetDateTime fechaDevolucionEstimada,
        OffsetDateTime fechaDevolucionReal,
        Short renovacionesRealizadas,
        Integer estadoPrestamoId
) {}
