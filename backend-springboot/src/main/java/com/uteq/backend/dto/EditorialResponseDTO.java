package com.uteq.backend.dto;

// Catálogo editoriales para los <select> del formulario de libros
// (FIX 3): mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
public record EditorialResponseDTO(
        Integer id,
        String nombre
) {}
