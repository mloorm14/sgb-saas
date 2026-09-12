package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Fila del listado paginado de {@code GET /api/v1/admin/usuarios} (vista de administración, no identidad de login).
 * <p>
 * {@code multasPendientes} se deriva de {@code estado} (¿el usuario está en
 * {@code BLOQUEADO_POR_MULTA}?) en vez de una consulta aparte a
 * {@code multas}: es exactamente la misma señal que ya usa
 * {@code UserDetailsServiceImpl} para bloquear el login (ver
 * {@code GlobalExceptionHandler#handleLocked}), así que no hace falta
 * duplicar esa lógica con un JOIN nuevo.
 */
public record UserListingResponseDTO(
        Long id, @JsonProperty("nombre") String name, @JsonProperty("apellido") String lastName, @JsonProperty("correo") String email,
        List<String> roles, @JsonProperty("estado") String status, @JsonProperty("multasPendientes") boolean finesPendientes
) {
}
