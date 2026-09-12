package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code PATCH /api/v1/admin/usuarios/{id}/rol}. El rol se valida contra la tabla roles en el service.
 */
public record ChangeRoleRequestDTO(
        @NotBlank(message = "El nuevo rol es obligatorio") @JsonProperty("nuevoRol") String freshRole
) {
}
