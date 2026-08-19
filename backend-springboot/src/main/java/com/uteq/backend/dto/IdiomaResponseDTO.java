package com.uteq.backend.dto;

// Catálogo idiomas para los <select> del formulario de libros (FIX 3):
// mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
public record IdiomaResponseDTO(
        Integer id,
        String nombre
) {}
