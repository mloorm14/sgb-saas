package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Fila del historial de devoluciones del bibliotecario.
 * Se muestra al entrar al módulo de devoluciones.
 */
public record LoanReturnHistoryDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("libroIsbn") String bookIsbn, @JsonProperty("usuarioNombre") String userName, @JsonProperty("fechaPrestamo") OffsetDateTime dateLoan, @JsonProperty("fechaDevolucionEstimada") OffsetDateTime dateLoanReturnEstimada, @JsonProperty("fechaDevolucionReal") OffsetDateTime dateLoanReturnReal, @JsonProperty("estadoDevolucion") String statusLoanReturn, @JsonProperty("montoTotalMultas") BigDecimal amountTotalFines, @JsonProperty("bibliotecarioNombre") String librarianName,
        @JsonProperty("fechaRegistro") OffsetDateTime dateRegistration
) {}
