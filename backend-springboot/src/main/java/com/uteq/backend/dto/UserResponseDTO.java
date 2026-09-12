package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record UserResponseDTO(
        Long id, @JsonProperty("nombre") String name, @JsonProperty("correo") String email,
        List<String> roles) {
}
