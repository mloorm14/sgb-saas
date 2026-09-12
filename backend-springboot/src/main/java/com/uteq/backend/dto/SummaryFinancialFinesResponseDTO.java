package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record SummaryFinancialFinesResponseDTO(
        @JsonProperty("totalRecaudado") BigDecimal totalRecaudado, @JsonProperty("totalPendiente") BigDecimal totalPending, @JsonProperty("totalGeneradoHoy") BigDecimal totalGeneratedToday, @JsonProperty("pagosRecientes") List<RecentPaymentDTO> paymentsRecientes
) {}
