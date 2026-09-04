package com.uteq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,

        @NotBlank(message = "El RUC es obligatorio")
        @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{13}$", message = "El RUC debe tener 13 dígitos numéricos sin guion")
        @Size(max = 20, message = "El RUC no puede superar 20 caracteres")
        String ruc,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección no puede superar 255 caracteres")
        String direccion,

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe ser válido")
        @Size(max = 150, message = "El email no puede superar 150 caracteres")
        String email,

        @Size(max = 150, message = "La persona de contacto no puede superar 150 caracteres")
        String personaContacto,

        Boolean activo
) {}
