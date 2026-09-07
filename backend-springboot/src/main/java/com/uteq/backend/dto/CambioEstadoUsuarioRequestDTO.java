package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de {@code PATCH /api/v1/admin/usuarios/{id}/estado}. El motivo es obligatorio para dejar rastro auditable.
 */
public record CambioEstadoUsuarioRequestDTO(
        @NotBlank(message = "El nuevo estado es obligatorio")
        String nuevoEstado,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
        String motivo
) {
}
