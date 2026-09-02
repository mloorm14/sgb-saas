package com.uteq.backend.dto;

public record ReporteInventarioResponseDTO(
        Long libroId,
        String titulo,
        String isbn,
        String autorNombre,
        String categoriaNombre,
        Short stockTotal,
        Short stockDisponible,
        String estadoDisponibilidad
) {}
