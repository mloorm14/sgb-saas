package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record UsuarioMotivoCambioResponseDTO(
        Long id,
        String tipoCambio,
        Integer estadoAnterior,
        Integer estadoNuevo,
        String motivo,
        Long ejecutadoPor,
        OffsetDateTime creadoEn) {
}
