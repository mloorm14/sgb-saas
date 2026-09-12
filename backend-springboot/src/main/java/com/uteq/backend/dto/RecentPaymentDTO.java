package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecentPaymentDTO( @JsonProperty("multaId") Long fineId, @JsonProperty("montoPagado") BigDecimal amountPaid, @JsonProperty("fechaPagada") OffsetDateTime datePaid, @JsonProperty("usuarioCorreo") String userEmail, @JsonProperty("usuarioNombre") String userName,
        @JsonProperty("libroTitulo") String bookTitle
) {}
