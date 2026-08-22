package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fila del historial reciente de préstamos de un usuario en la ventanilla
 * (GET /api/v1/prestamos/gestion/historial). El frontend lo pinta como
 * línea de tiempo: ícono según estadoNombre + multaPendiente.
 *
 * A diferencia de PrestamoResponseDTO (sin título de libro), acá el título
 * viaja resuelto y se agrega la multa pendiente asociada al préstamo (si
 * existe) para poder mostrar "Devuelto tarde (Multa pendiente)".
 */
public record HistorialPrestamoDTO(
        Long prestamoId,
        Long libroId,
        String libroTitulo,
        String libroIsbn,
        List<String> autores,
        List<String> categorias,
        OffsetDateTime fechaPrestamo,
        OffsetDateTime fechaDevolucionEstimada,
        OffsetDateTime fechaDevolucionReal,
        String estadoNombre,
        boolean multaPendiente,
        BigDecimal montoMultaPendiente
) {}
