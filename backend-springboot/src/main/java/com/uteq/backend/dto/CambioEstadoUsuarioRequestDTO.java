package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de {@code PATCH /api/v1/admin/usuarios/{id}/estado} (Módulo 5.4).
 * {@code motivo} es obligatorio (no opcional): todo cambio de estado
 * (bloqueo/activación manual por un ADMIN, distinto del bloqueo automático
 * por multas) queda en {@code bitacora_auditoria} y sin motivo esa fila no
 * sirve para auditar nada después.
 */
public record CambioEstadoUsuarioRequestDTO(
        @NotBlank(message = "El nuevo estado es obligatorio")
        String nuevoEstado,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
        String motivo
) {
}
