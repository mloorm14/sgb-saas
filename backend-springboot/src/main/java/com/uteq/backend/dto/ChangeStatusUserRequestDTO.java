package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de {@code PATCH /api/v1/admin/usuarios/{id}/estado}. El motivo es obligatorio para dejar rastro auditable.
 */
public record ChangeStatusUserRequestDTO(
        @NotBlank(message = "El nuevo estado es obligatorio") @JsonProperty("nuevoEstado") String freshStatus,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
        String reason
) {
}
