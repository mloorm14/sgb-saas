package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserAdminRequestDTO(
        @NotBlank @Size(min = 2, max = 100) @JsonProperty("nombre") String name,
        @NotBlank @Size(min = 2, max = 100) @JsonProperty("apellido") String lastName,
        @NotBlank @Email @JsonProperty("correo") String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @JsonProperty("rol") String role
) {}
