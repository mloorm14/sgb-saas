package com.uteq.backend.dto;

import java.util.List;

/**
 * Fila del listado paginado de {@code GET /api/v1/admin/usuarios}
 * (Módulo 5). No reutiliza {@link UsuarioResponseDTO} (el DTO que ya
 * devuelve AuthService en login/registro) porque ese es la identidad del
 * usuario autenticado consigo mismo, mientras que este es una vista de
 * administración: agrega {@code apellido}, {@code estado} y
 * {@code multasPendientes}, que no tienen sentido en el contexto de login.
 * <p>
 * {@code multasPendientes} se deriva de {@code estado} (¿el usuario está en
 * {@code BLOQUEADO_POR_MULTA}?) en vez de una consulta aparte a
 * {@code multas}: es exactamente la misma señal que ya usa
 * {@code UserDetailsServiceImpl} para bloquear el login (ver
 * {@code GlobalExceptionHandler#handleLocked}), así que no hace falta
 * duplicar esa lógica con un JOIN nuevo.
 */
public record UsuarioListadoResponseDTO(
        Long id,
        String nombre,
        String apellido,
        String correo,
        List<String> roles,
        String estado,
        boolean multasPendientes
) {
}
