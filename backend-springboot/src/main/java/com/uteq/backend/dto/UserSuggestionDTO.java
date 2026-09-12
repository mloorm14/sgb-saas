package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Resultado ligero para el autocompletado de usuarios en la ventanilla
 * de préstamos (GET /api/v1/prestamos/gestion/sugerencias-usuarios).
 * Solo los campos necesarios para el dropdown predictivo.
 */
public record UserSuggestionDTO(
        Long id, @JsonProperty("nombreCompleto") String nameFull, @JsonProperty("correo") String email,
        @JsonProperty("estadoCuenta") String statusAccount
) {}
