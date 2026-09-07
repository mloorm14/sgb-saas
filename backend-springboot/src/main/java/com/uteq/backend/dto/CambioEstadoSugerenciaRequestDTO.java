package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Body para aprobar o rechazar una sugerencia (nuevoEstado: APROBADA|RECHAZADA).
public record CambioEstadoSugerenciaRequestDTO(

        @NotBlank(message = "El nuevo estado es obligatorio")
        @Pattern(regexp = "APROBADA|RECHAZADA", message = "El estado debe ser APROBADA o RECHAZADA")
        String nuevoEstado
) {}
