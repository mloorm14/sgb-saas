package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

// Refleja 1:1 las keys del {@code Map<String,Object>} que retorna
// PrestamoProcedureRepository.spRegistrarDevolucion (o_prestamo_id,
// o_hubo_multa, o_monto_multa) -- ver sp_registrar_devolucion en db/procs/.
public record LoanReturnResponseDTO( @JsonProperty("prestamoId") Long loanId, @JsonProperty("huboMulta") Boolean huboFine, @JsonProperty("montoMulta") BigDecimal amountFine
) {}
