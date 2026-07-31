package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

// refreshToken viaja SOLO como cookie HttpOnly+Secure+SameSite=Strict (ver
// AuthController), nunca en el cuerpo JSON: si además viajara aquí, la
// protección HttpOnly (JS no puede leer la cookie) quedaría anulada por
// tener el mismo valor accesible en response.refreshToken() vía JS. El
// campo se conserva en el record (no en el JSON) porque AuthController
// necesita el valor real para construir el Set-Cookie de la respuesta.
public record TokenResponseDTO(String accessToken, @JsonIgnore String refreshToken, long expiresIn, String tokenType) {

    public TokenResponseDTO(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
