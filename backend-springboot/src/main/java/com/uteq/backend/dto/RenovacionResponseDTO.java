package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record RenovacionResponseDTO(
        Long prestamoId,
        OffsetDateTime nuevaFechaDevolucionEstimada,
        Short renovacionesRealizadas,
        Short renovacionesRestantes
) {}
