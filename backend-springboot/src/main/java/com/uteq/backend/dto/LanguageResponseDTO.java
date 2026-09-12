package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


// Catálogo idiomas para los <select> del formulario de libros (FIX 3):
// mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
public record LanguageResponseDTO(
        Integer id, @JsonProperty("nombre") String name
) {}
