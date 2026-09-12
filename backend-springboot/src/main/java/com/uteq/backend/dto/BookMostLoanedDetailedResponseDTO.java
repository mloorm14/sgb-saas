package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BookMostLoanedDetailedResponseDTO( @JsonProperty("libroId") Long bookId, @JsonProperty("titulo") String title,
        String isbn, @JsonProperty("autorNombre") String authorName, @JsonProperty("categoriaNombre") String categoryName, @JsonProperty("totalPrestamos") Long totalLoans, @JsonProperty("porcentaje") BigDecimal percentage
) {}
