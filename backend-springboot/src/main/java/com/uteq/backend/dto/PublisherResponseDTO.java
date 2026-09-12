package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


// Catálogo editoriales para los <select> del formulario de libros
// (FIX 3): mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
public record PublisherResponseDTO(
        Integer id, @JsonProperty("nombre") String name
) {}
