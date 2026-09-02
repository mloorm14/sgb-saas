package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PagoRecienteDTO(
        Long multaId,
        BigDecimal montoPagado,
        OffsetDateTime fechaPagada,
        String usuarioCorreo,
        String usuarioNombre,
        String libroTitulo
) {}
