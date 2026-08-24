package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de POST /api/v1/prestamos/{id}/devolucion.
 * Resume el resultado completo de la devolución: multa por atraso,
 * multa por daño, y detalle de daños registrados.
 */
public record DevolucionCompletaResponseDTO(
        Long prestamoId,
        Long registroDanoId,
        boolean huboMultaAtraso,
        BigDecimal montoMultaAtraso,
        boolean huboMultaDano,
        BigDecimal montoMultaDano,
        BigDecimal montoTotal,
        List<DanoDetalleResponseDTO> danosRegistrados
) {}
