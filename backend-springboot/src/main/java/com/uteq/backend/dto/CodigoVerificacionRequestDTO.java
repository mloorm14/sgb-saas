package com.uteq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodigoVerificacionRequestDTO(
        @NotBlank
        @Email
        String correo,

        // Exactamente 6 dígitos -- el mismo formato que genera
        // VerificacionCorreoService.generarYEnviarCodigo(). @Pattern (no
        // @Size) para rechazar de una vez algo como "12a456" antes de
        // siquiera consultar Redis.
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "El código debe tener exactamente 6 dígitos")
        String codigo
) {
}
