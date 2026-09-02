package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record ResumenCategoriaAuditoriaDTO(
        String tablaAfectada,
        long totalEventos,
        long eventosHoy,
        OffsetDateTime ultimoEvento,
        boolean requiereRevision
) {
}
