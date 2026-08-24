package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record ReservacionHoyResponseDTO(
        Long reservacionId,
        String usuarioNombre,
        String usuarioCorreo,
        String libroTitulo,
        String estadoNombre,
        OffsetDateTime fechaLimiteRetiro
) {}
