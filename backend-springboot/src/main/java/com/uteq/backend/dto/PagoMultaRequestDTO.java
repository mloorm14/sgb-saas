package com.uteq.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body de POST /api/v1/multas/{id}/pago con monto parcial.
 * Si no se envía body (null), se asume pago completo (backward compatible).
 */
public record PagoMultaRequestDTO(
        @NotNull(message = "El monto a pagar es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal montoPagado
) {}
