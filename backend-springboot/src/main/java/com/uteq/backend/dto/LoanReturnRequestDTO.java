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
     * Procesa damage item dto y devuelve el resultado calculado por el backend.
     *
     * @param typeDamageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param nameCustom valor de entrada nameCustom usado por la operacion para completar su regla de negocio
     * @param priceCobrado valor de entrada priceCobrado usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public record DamageItemDTO(
            Integer typeDamageId,
            String nameCustom,
            BigDecimal priceCobrado
    ) {}
}
