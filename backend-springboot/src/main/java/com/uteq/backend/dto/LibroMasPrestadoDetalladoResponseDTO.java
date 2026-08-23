package com.uteq.backend.dto;

import java.math.BigDecimal;

public record LibroMasPrestadoDetalladoResponseDTO(
        Long libroId,
        String titulo,
        String isbn,
        String autorNombre,
        String categoriaNombre,
        Long totalPrestamos,
        BigDecimal porcentaje
) {}
