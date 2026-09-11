package com.uteq.backend.dto;

import java.math.BigDecimal;

/**
 * Tipo de daño del catálogo (tipos_dano).
 */
public record TipoDanoDTO(
        Integer id,
        String nombre,
        Integer categoriaId,
        String categoriaNombre,
        String tipoCosto,
        BigDecimal valor
) {
    /**
     * Executes the precio operation.
     * @return operation result
     */
    public BigDecimal precio() { return valor; }
}
