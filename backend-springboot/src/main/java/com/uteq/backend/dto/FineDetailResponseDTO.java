package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO enriquecido para el listado de multas con información del libro,
 * fechas del préstamo y saldos de pago parcial.
 * Endpoint: GET /api/v1/multas/usuario/{id}/detalle
 */
public record FineDetailResponseDTO(
        @JsonProperty("prestamoId") Long id, Long loanId, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("libroIsbn") String bookIsbn, @JsonProperty("observaciones") String observations, @JsonProperty("monto") BigDecimal amount, @JsonProperty("montoPagado") BigDecimal amountPaid, @JsonProperty("saldo") BigDecimal balance, @JsonProperty("estadoMultaId") Integer statusFineId, @JsonProperty("estadoNombre") String statusName, @JsonProperty("fechaGenerada") OffsetDateTime dateGenerated, @JsonProperty("fechaPagada") OffsetDateTime datePaid, @JsonProperty("fechaPrestamoInicio") OffsetDateTime dateLoanStart, @JsonProperty("fechaPrestamoFin") OffsetDateTime dateLoanFin, @JsonProperty("diasAtraso") int daysAtraso
) {}
