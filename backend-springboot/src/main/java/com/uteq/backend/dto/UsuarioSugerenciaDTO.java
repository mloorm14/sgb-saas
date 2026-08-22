package com.uteq.backend.dto;

/**
 * Resultado ligero para el autocompletado de usuarios en la ventanilla
 * de préstamos (GET /api/v1/prestamos/gestion/sugerencias-usuarios).
 * Solo los campos necesarios para el dropdown predictivo.
 */
public record UsuarioSugerenciaDTO(
        Long id,
        String nombreCompleto,
        String correo,
        String estadoCuenta
) {}
