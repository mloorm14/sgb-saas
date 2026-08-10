package com.uteq.backend.dto;

import java.math.BigDecimal;

// DTO de respuesta HTTP para fn_reporte_indice_morosidad -- mismo criterio
// que LibroMasPrestadoResponseDTO: envuelve la proyección en vez de
// exponerla directamente en la respuesta.
public record ReporteMorosidadResponseDTO(
        Long usuarioId,
        String nombre,
        String apellido,
        String correo,
        BigDecimal montoTotalAdeudado,
        Long cantidadMultasPendientes,
        BigDecimal diasAtrasoPromedio
) {}
