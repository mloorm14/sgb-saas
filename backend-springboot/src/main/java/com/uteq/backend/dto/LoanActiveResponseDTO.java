package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// DTO de respuesta HTTP para fn_listar_prestamos_activos_por_usuario --
// envuelve PrestamoActivoProjection en lugar de expuesta directamente
// para no acoplar el contrato de la API a la forma exacta de la
// proyección JPA (mismo criterio de "nunca expongas la entidad/proyección
// directamente" aplicado también a proyecciones, no solo a entidades).
public record LoanActiveResponseDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("libroIsbn") String bookIsbn, @JsonProperty("fechaPrestamo") OffsetDateTime dateLoan, @JsonProperty("fechaDevolucionEstimada") OffsetDateTime dateLoanReturnEstimada, @JsonProperty("diasRestantes") Integer daysRestantes,
        @JsonProperty("estadoNombre") String statusName
) {}
