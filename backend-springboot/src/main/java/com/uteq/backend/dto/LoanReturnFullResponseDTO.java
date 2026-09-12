package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de POST /api/v1/prestamos/{id}/devolucion.
 * Resume el resultado completo de la devolución: multa por atraso,
 * multa por daño, y detalle de daños registrados.
 */
public record LoanReturnFullResponseDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("registroDanoId") Long registrationDamageId, @JsonProperty("huboMultaAtraso") boolean huboFineAtraso, @JsonProperty("montoMultaAtraso") BigDecimal amountFineAtraso, @JsonProperty("huboMultaDano") boolean huboFineDamage, @JsonProperty("montoMultaDano") BigDecimal amountFineDamage, @JsonProperty("montoTotal") BigDecimal amountTotal, @JsonProperty("danosRegistrados") List<DamageDetailResponseDTO> damagesRegistrados
) {}
