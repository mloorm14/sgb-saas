package com.uteq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,

        @Size(max = 20, message = "El RUC no puede superar 20 caracteres")
        String ruc,

        @Size(max = 255, message = "La dirección no puede superar 255 caracteres")
        String direccion,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,

        @Email(message = "El email debe ser válido")
        @Size(max = 150, message = "El email no puede superar 150 caracteres")
        String email,

        @Size(max = 150, message = "La persona de contacto no puede superar 150 caracteres")
        String personaContacto,

        Boolean activo
) {}
