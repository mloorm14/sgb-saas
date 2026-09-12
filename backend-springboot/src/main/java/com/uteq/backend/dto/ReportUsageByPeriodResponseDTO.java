package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// DTO de respuesta HTTP para fn_reporte_uso_por_periodo -- mismo criterio
// que LibroMasPrestadoResponseDTO: envuelve la proyección en vez de
// exponerla directamente en la respuesta.
public record ReportUsageByPeriodResponseDTO( @JsonProperty("periodo") OffsetDateTime period, @JsonProperty("totalPrestamos") Long totalLoans, @JsonProperty("totalDevoluciones") Long totalLoanReturns
) {}
