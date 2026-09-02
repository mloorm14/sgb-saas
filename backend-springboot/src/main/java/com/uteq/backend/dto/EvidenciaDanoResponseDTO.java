package com.uteq.backend.dto;

import java.time.OffsetDateTime;

public record EvidenciaDanoResponseDTO(
        Long id,
        Long registroDanoId,
        String archivoNombre,
        String archivoTipo,
        OffsetDateTime subidoEn
) {}
