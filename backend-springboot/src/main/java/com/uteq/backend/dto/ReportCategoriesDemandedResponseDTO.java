package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ReportCategoriesDemandedResponseDTO( @JsonProperty("categoriaId") Integer categoryId, @JsonProperty("categoriaNombre") String categoryName, @JsonProperty("totalPrestamos") Long totalLoans, @JsonProperty("porcentaje") BigDecimal percentage
) {}
