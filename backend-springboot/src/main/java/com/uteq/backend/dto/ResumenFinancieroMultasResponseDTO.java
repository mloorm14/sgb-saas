package com.uteq.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumenFinancieroMultasResponseDTO(
        BigDecimal totalRecaudado,
        BigDecimal totalPendiente,
        BigDecimal totalGeneradoHoy,
        List<PagoRecienteDTO> pagosRecientes
) {}
