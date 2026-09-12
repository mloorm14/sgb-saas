package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestResetRequestDTO(
        @NotBlank @Email @JsonProperty("correo") String email
) {}
