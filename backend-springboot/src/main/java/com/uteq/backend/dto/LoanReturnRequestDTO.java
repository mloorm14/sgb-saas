package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body de POST /api/v1/prestamos/{id}/devolucion.
 * Contiene el estado de la devolución y opcionalmente los daños registrados.
 */
public record LoanReturnRequestDTO(

        @NotBlank(message = "El estado de devolucion es obligatorio") @JsonProperty("estadoDevolucion") String statusLoanReturn, @JsonProperty("descripcion") String description, @JsonProperty("danos") List<DamageItemDTO> damages
) {
    /**
     * Cada tipo de daño seleccionado. Si tipoDanoId es null, se espera
     * nombreCustom (daño "Otro").
     */
    public record DamageItemDTO(
            Integer typeDamageId,
            String nameCustom,
            BigDecimal priceCobrado
    ) {}
}
