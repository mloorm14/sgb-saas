package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO enriquecido para el listado de multas con información del libro,
 * fechas del préstamo y saldos de pago parcial.
 * Endpoint: GET /api/v1/multas/usuario/{id}/detalle
 */
public record MultaDetalleResponseDTO(
        Long id,
        Long prestamoId,
        String libroTitulo,
        String libroIsbn,
        String observaciones,
        BigDecimal monto,
        BigDecimal montoPagado,
        BigDecimal saldo,
        Integer estadoMultaId,
        String estadoNombre,
        OffsetDateTime fechaGenerada,
        OffsetDateTime fechaPagada,
        OffsetDateTime fechaPrestamoInicio,
        OffsetDateTime fechaPrestamoFin,
        int diasAtraso
) {}
