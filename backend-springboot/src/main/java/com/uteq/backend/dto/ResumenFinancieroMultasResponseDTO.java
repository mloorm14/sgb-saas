package com.uteq.backend.dto;

import java.math.BigDecimal;

// DTO de respuesta HTTP para fn_reporte_resumen_financiero_multas -- mismo
// criterio que ReporteMorosidadResponseDTO: envuelve la proyección en vez
// de exponerla directamente en la respuesta.
public record ResumenFinancieroMultasResponseDTO(
        BigDecimal totalRecaudado,
        BigDecimal totalPendiente
) {}
