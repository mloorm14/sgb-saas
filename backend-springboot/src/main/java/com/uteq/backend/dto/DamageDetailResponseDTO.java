package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Detalle de un tipo de daño registrado en una devolución.
 */
public record DamageDetailResponseDTO(
        Long id, @JsonProperty("tipoDanoNombre") String typeDamageName, @JsonProperty("nombreCustom") String nameCustom, @JsonProperty("precioCobrado") BigDecimal priceCobrado
) {}
