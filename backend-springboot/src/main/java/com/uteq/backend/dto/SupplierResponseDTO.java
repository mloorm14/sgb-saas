package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


public record SupplierResponseDTO(
        Integer id, @JsonProperty("nombre") String name,
        String ruc,
        String direccion,
        String telefono,
        String email,
        String personaContacto, @JsonProperty("activo") Boolean active
) {}
