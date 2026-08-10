package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code PATCH /api/v1/admin/usuarios/{id}/rol} (Módulo 5.4).
 * A propósito no se valida {@code nuevoRol} contra un enum fijo en el DTO
 * (p.ej. {@code @Pattern}): el catálogo real de roles vive en la tabla
 * {@code roles} (ver {@code RolRepository}), así que
 * {@code UsuarioAdminService#cambiarRol} es quien resuelve si el valor
 * recibido existe -- mismo criterio que
 * {@code ConfiguracionSistemaService#actualizar} no inventa claves nuevas
 * por fuera del catálogo ya sembrado.
 */
public record CambioRolRequestDTO(
        @NotBlank(message = "El nuevo rol es obligatorio")
        String nuevoRol
) {
}
