package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequestDTO(
        @NotBlank @JsonProperty("nombre") String name,

        @NotBlank @JsonProperty("apellido") String lastName,

        @NotBlank
        @Email
        @JsonProperty("correo") String email,

        @NotBlank
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {
}
