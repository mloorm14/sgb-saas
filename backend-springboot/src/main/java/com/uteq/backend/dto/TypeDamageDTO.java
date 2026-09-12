package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Tipo de daño del catálogo (tipos_dano).
 */
public record TypeDamageDTO(
        Integer id, @JsonProperty("nombre") String name, @JsonProperty("categoriaId") Integer categoryId, @JsonProperty("categoriaNombre") String categoryName, @JsonProperty("tipoCosto") String typeCost, @JsonProperty("valor") BigDecimal value
) {
    /**
     * Handles precio.
     *
     * @return big decimal with the resulting state after the operation
     */
    public BigDecimal price() { return value; }
}
