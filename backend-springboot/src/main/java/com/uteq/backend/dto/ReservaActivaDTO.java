package com.uteq.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reserva vigente de un usuario en la ventanilla de préstamos (GET
 * /api/v1/prestamos/gestion/reserva-activa?usuarioId=). "Vigente" = estado
 * PENDIENTE o LISTA_PARA_RETIRO (mismo criterio que PrestamoService para
 * bloquear renovaciones). Responde 404 con ProblemDetail si no existe, lo
 * que el frontend interpreta como "Caso B: préstamo directo".
 *
 * Los datos del libro viajan resueltos para que la tarjeta "Nuevo Préstamo"
 * no tenga que hacer N consultas extra.
 */
public record ReservaActivaDTO(
        Long reservacionId,
        Long libroId,
        String titulo,
        List<String> autores,
        String isbn,
        OffsetDateTime fechaReserva,
        OffsetDateTime fechaLimiteRetiro,
        Integer diasPrestamoSugerido,
        Short anioPublicacion,
        Short stockDisponible,
        Short stockTotal,
        String ubicacionFisica,
        List<String> categorias,
        boolean tienePortada
) {}
