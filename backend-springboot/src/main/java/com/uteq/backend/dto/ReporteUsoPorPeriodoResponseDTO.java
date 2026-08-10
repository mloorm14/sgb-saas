package com.uteq.backend.dto;

import java.time.OffsetDateTime;

// DTO de respuesta HTTP para fn_reporte_uso_por_periodo -- mismo criterio
// que LibroMasPrestadoResponseDTO: envuelve la proyección en vez de
// exponerla directamente en la respuesta.
public record ReporteUsoPorPeriodoResponseDTO(
        OffsetDateTime periodo,
        Long totalPrestamos,
        Long totalDevoluciones
) {}
