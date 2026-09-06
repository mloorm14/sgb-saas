package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record ReservacionHoyResponseDTO(
        Long reservacionId,
        String usuarioNombre,
        String usuarioCorreo,
        String libroTitulo,
        String libroIsbn,
        String estadoNombre,
        OffsetDateTime fechaLimiteRetiro
) {}
