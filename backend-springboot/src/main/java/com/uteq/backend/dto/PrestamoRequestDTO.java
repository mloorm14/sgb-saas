package com.uteq.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Crea un préstamo (usuarioId o credencialQrToken, exactamente uno; reservacionId opcional la vincula y marca RETIRADA).
public record PrestamoRequestDTO(

        Long usuarioId,

        UUID credencialQrToken,

        @NotNull(message = "El libro es obligatorio")
        Long libroId,

        @NotNull(message = "Los días de préstamo son obligatorios")
        @Min(value = 1, message = "Los días de préstamo deben ser al menos 1")
        Integer diasPrestamo,

        Long reservacionId
) {}
