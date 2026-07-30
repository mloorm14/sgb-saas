package com.uteq.backend.dto;

// DTO de respuesta HTTP para fn_reporte_libros_mas_prestados -- mismo
// criterio que PrestamoActivoResponseDTO: envuelve la proyección en vez
// de exponerla directamente en la respuesta.
public record LibroMasPrestadoResponseDTO(
        Long libroId,
        String titulo,
        String isbn,
        Long totalPrestamos
) {}
