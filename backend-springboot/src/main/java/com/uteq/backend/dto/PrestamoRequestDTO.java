package com.uteq.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// bibliotecarioId NO viaja en el body: se resuelve en el service a partir
// del Authentication (mismo principio de seguridad que p_rol_ejecutor en
// MultaService.anular — no confiar en el cliente para atribuir quién
// ejecuta la acción).
//
// usuarioId es NULLABLE a propósito (Módulo 8, credencial QR): el
// bibliotecario puede identificar al lector escaneando su QR
// (credencialQrToken) en vez de escribir el id a mano. Exactamente uno de
// los dos debe venir; se valida en PrestamoService.crear() y no aquí con
// Bean Validation porque un "exactamente uno de estos dos campos" necesita
// una anotación @AssertTrue adicional que complica más de lo que
// simplifica para un caso tan puntual. El ingreso manual (usuarioId) se
// mantiene siempre disponible como contingencia obligatoria para cuando el
// dispositivo del estudiante falla.
public record PrestamoRequestDTO(

        Long usuarioId,

        UUID credencialQrToken,

        @NotNull(message = "El libro es obligatorio")
        Long libroId,

        @NotNull(message = "Los días de préstamo son obligatorios")
        @Min(value = 1, message = "Los días de préstamo deben ser al menos 1")
        Integer diasPrestamo
) {}
