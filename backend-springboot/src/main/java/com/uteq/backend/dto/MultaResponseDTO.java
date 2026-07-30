package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MultaResponseDTO(
        Long id,
        Long prestamoId,
        BigDecimal monto,
        Integer estadoMultaId,
        OffsetDateTime fechaGenerada,
        OffsetDateTime fechaPagada,
        String observaciones
) {}