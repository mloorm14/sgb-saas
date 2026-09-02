package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReporteVencidosResponseDTO(
        Long prestamoId,
        String usuarioNombre,
        String usuarioCorreo,
        String libroTitulo,
        String libroIsbn,
        OffsetDateTime fechaDevolucionEstimada,
        Long diasAtraso,
        BigDecimal montoMultaEstimada
) {}
