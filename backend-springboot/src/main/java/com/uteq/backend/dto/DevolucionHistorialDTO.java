package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Fila del historial de devoluciones del bibliotecario.
 * Se muestra al entrar al módulo de devoluciones.
 */
public record DevolucionHistorialDTO(
        Long prestamoId,
        String libroTitulo,
        String libroIsbn,
        String usuarioNombre,
        OffsetDateTime fechaPrestamo,
        OffsetDateTime fechaDevolucionEstimada,
        OffsetDateTime fechaDevolucionReal,
        String estadoDevolucion,
        BigDecimal montoTotalMultas,
        String bibliotecarioNombre,
        OffsetDateTime fechaRegistro
) {}
