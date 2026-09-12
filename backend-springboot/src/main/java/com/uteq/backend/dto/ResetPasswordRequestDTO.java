package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank @Email @JsonProperty("correo") String email,
        @NotBlank @JsonProperty("codigo") String code,
        @NotBlank @Size(min = 8, max = 72) String freshPassword
) {}
