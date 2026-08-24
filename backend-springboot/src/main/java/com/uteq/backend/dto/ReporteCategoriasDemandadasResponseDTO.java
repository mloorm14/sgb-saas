package com.uteq.backend.dto;

import java.math.BigDecimal;

public record ReporteCategoriasDemandadasResponseDTO(
        Integer categoriaId,
        String categoriaNombre,
        Long totalPrestamos,
        BigDecimal porcentaje
) {}
