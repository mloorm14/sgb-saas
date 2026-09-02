package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body de POST /api/v1/prestamos/{id}/devolucion.
 * Contiene el estado de la devolución y opcionalmente los daños registrados.
 */
public record DevolucionRequestDTO(

        @NotBlank(message = "El estado de devolucion es obligatorio")
        String estadoDevolucion,

        String descripcion,

        List<DanoItemDTO> danos
) {
    /**
     * Cada tipo de daño seleccionado. Si tipoDanoId es null, se espera
     * nombreCustom (daño "Otro").
     */
    public record DanoItemDTO(
            Integer tipoDanoId,
            String nombreCustom,
            BigDecimal precioCobrado
    ) {}
}
