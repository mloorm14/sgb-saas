package com.uteq.backend.dto;

// Fila agregada de sugerencias PENDIENTE por ISBN (gestión por demanda:
// lo más pedido se adquiere primero). titulo/autor son MAX() del grupo
// porque el mismo ISBN puede venir con variantes de tipeo.
public record SugerenciaAgrupadaDTO(
        String isbn,
        String titulo,
        String autor,
        Long cantidad
) {}
