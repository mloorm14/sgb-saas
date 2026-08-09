package com.uteq.backend.dto;

import java.time.OffsetDateTime;

// Expone tipoNotificacionId como identificador plano (no el nombre
// resuelto del catálogo), mismo criterio que MultaResponseDTO.estadoMultaId
// -- MultaService.toDTO() tampoco resuelve el nombre del estado, para no
// pagar un join/consulta extra solo por legibilidad en un listado paginado.
public record NotificacionResponseDTO(
        Long id,
        Long prestamoId,
        Integer tipoNotificacionId,
        String mensaje,
        OffsetDateTime fechaEnvio,
        boolean enviadoOk,
        OffsetDateTime creadoEn
) {
}
