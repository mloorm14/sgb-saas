package com.uteq.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// bibliotecarioId NO viaja en el body: se resuelve en el service a partir
// del Authentication (mismo principio de seguridad que p_rol_ejecutor en
// MultaService.anular — no confiar en el cliente para atribuir quién
// ejecuta la acción).
public record PrestamoRequestDTO(

        @NotNull(message = "El usuario es obligatorio")
        Long usuarioId,

        @NotNull(message = "El libro es obligatorio")
        Long libroId,

        @NotNull(message = "Los días de préstamo son obligatorios")
        @Min(value = 1, message = "Los días de préstamo deben ser al menos 1")
        Integer diasPrestamo
) {}
