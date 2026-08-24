package com.uteq.backend.dto;

import java.math.BigDecimal;

/**
 * Tipo de daño del catálogo (tipos_dano).
 */
public record TipoDanoDTO(
        Integer id,
        String nombre,
        BigDecimal precio
) {}
