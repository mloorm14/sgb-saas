package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record ReservacionResponseDTO(
        Long id,
        Long usuarioId,
        Long libroId,
        Integer estadoReservacionId,
        OffsetDateTime fechaReserva,
        OffsetDateTime fechaLimiteRetiro
) {}