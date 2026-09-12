package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


public record AuthorResponseDTO(
        Long id, @JsonProperty("nombre") String name
) {}
