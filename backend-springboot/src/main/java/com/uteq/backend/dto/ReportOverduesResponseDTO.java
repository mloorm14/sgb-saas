package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReportOverduesResponseDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("usuarioNombre") String userName, @JsonProperty("usuarioCorreo") String userEmail, @JsonProperty("libroTitulo") String bookTitle, @JsonProperty("libroIsbn") String bookIsbn, @JsonProperty("fechaDevolucionEstimada") OffsetDateTime dateLoanReturnEstimada, @JsonProperty("diasAtraso") Long daysAtraso, @JsonProperty("montoMultaEstimada") BigDecimal amountFineEstimada
) {}
