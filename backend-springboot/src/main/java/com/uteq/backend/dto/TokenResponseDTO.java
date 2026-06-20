package com.uteq.backend.dto;

public record TokenResponseDTO(String accessToken, String refreshToken, long expiresIn, String tokenType) {

    public TokenResponseDTO(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
