package com.uteq.backend.dto;

import java.time.OffsetDateTime;

/**
 * Fila del historial de reservaciones de un usuario
 * (GET /api/v1/reservaciones/gestion/historial-reservaciones?usuarioId=).
 *
 * El frontend lo pinta como tarjetas con badge de estado coloreado.
 * El título del libro viene resuelto para evitar llamadas N+1.
 */
public record HistorialReservacionDTO(
        Long reservacionId,
        String libroTitulo,
        String estadoNombre,
        Integer estadoId,
        OffsetDateTime fechaReserva,
        OffsetDateTime fechaLimiteRetiro
) {}
