package com.uteq.backend.dto;

public record ProveedorResponseDTO(
        Integer id,
        String nombre,
        String ruc,
        String direccion,
        String telefono,
        String email,
        String personaContacto,
        Boolean activo
) {}
