package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


// Catálogo estados_libro para los <select> del formulario de libros
// (FIX 3): mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
// El nombre ya es legible ("Activo", "Dado de baja"); el codigo interno
// (ACTIVO/DADO_DE_BAJA) no se expone, el frontend solo necesita id+nombre.
public record StatusBookResponseDTO(
        Integer id, @JsonProperty("nombre") String name
) {}
