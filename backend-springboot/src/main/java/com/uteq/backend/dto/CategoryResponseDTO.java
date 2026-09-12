package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


public record CategoryResponseDTO(
        Integer id, @JsonProperty("nombre") String name
) {}
