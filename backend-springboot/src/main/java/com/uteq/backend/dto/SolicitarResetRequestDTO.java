package com.uteq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarResetRequestDTO(
        @NotBlank @Email String correo
) {}
