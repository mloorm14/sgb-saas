package com.uteq.backend.dto;

import java.math.BigDecimal;

/**
 * Detalle de un tipo de daño registrado en una devolución.
 */
public record DanoDetalleResponseDTO(
        Long id,
        String tipoDanoNombre,
        String nombreCustom,
        BigDecimal precioCobrado
) {}
