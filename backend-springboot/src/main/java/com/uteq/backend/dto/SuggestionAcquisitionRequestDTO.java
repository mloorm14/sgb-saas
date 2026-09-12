package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SuggestionAcquisitionRequestDTO(

        @NotBlank(message = "El título sugerido es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres") @JsonProperty("titulo") String title,

        @Size(max = 150, message = "El autor no puede superar 150 caracteres") String author,

        @Pattern(regexp = "^[0-9]{13}$", message = "El ISBN debe tener 13 dígitos numéricos, sin guiones")
        String isbn,

        @Size(max = 1000, message = "La justificación no puede superar 1000 caracteres")
        String justificacion
) {}
