package com.uteq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank @Email String correo,
        @NotBlank String codigo,
        @NotBlank @Size(min = 8, max = 72) String nuevaPassword
) {}
