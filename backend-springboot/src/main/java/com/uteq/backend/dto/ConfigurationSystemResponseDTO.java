package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


public record ConfigurationSystemResponseDTO( @JsonProperty("clave") String key,
        @JsonProperty("valor") String value
) {}
