package com.uteq.backend.dto;

// Catálogo estados_libro para los <select> del formulario de libros
// (FIX 3): mismo contrato que CategoriaResponseDTO/AutorResponseDTO.
// El nombre ya es legible ("Activo", "Dado de baja"); el codigo interno
// (ACTIVO/DADO_DE_BAJA) no se expone, el frontend solo necesita id+nombre.
public record EstadoLibroResponseDTO(
        Integer id,
        String nombre
) {}
