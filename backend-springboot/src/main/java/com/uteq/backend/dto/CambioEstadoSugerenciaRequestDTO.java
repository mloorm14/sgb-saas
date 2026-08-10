package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// No incluido en la lista original de DTOs de la rama E, pero necesario:
// el roadmap dice "gerente lista y cambia estado" para el Módulo 9.3, y
// ese cambio de estado necesita su propio body (nuevoEstado) además del
// GET de listado -- mismo criterio que CambioEstadoUsuarioRequestDTO del
// Módulo 5 (rama F, aún no implementada), adelantado acá solo para este
// caso puntual.
public record CambioEstadoSugerenciaRequestDTO(

        @NotBlank(message = "El nuevo estado es obligatorio")
        @Pattern(regexp = "APROBADA|RECHAZADA", message = "El estado debe ser APROBADA o RECHAZADA")
        String nuevoEstado
) {}
