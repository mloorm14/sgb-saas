package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(
        @NotBlank
        String refreshToken
) {
}
