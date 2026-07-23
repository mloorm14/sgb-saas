package com.uteq.backend.dto;

import java.time.OffsetDateTime;

// DTO de respuesta HTTP para fn_listar_prestamos_activos_por_usuario --
// envuelve PrestamoActivoProjection en lugar de expuesta directamente
// para no acoplar el contrato de la API a la forma exacta de la
// proyección JPA (mismo criterio de "nunca expongas la entidad/proyección
// directamente" aplicado también a proyecciones, no solo a entidades).
public record PrestamoActivoResponseDTO(
        Long prestamoId,
        String libroTitulo,
        String libroIsbn,
        OffsetDateTime fechaPrestamo,
        OffsetDateTime fechaDevolucionEstimada,
        Integer diasRestantes,
        String estadoNombre
) {}
