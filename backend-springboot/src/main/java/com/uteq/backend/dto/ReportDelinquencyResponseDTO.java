package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

// DTO de respuesta HTTP para fn_reporte_indice_morosidad -- mismo criterio
// que LibroMasPrestadoResponseDTO: envuelve la proyección en vez de
// exponerla directamente en la respuesta.
public record ReportDelinquencyResponseDTO( @JsonProperty("usuarioId") Long userId, @JsonProperty("nombre") String name, @JsonProperty("apellido") String lastName, @JsonProperty("correo") String email, @JsonProperty("montoTotalAdeudado") BigDecimal amountTotalAdeudado, @JsonProperty("cantidadMultasPendientes") Long quantityFinesPendientes,
        @JsonProperty("diasAtrasoPromedio") BigDecimal daysAtrasoPromedio
) {}
