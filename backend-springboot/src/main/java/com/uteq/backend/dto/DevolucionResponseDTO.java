package com.uteq.backend.dto;

import java.math.BigDecimal;

// Refleja 1:1 las keys del Map<String,Object> que retorna
// PrestamoProcedureRepository.spRegistrarDevolucion (o_prestamo_id,
// o_hubo_multa, o_monto_multa) -- ver sp_registrar_devolucion en db/procs/.
public record DevolucionResponseDTO(
        Long prestamoId,
        Boolean huboMulta,
        BigDecimal montoMulta
) {}
