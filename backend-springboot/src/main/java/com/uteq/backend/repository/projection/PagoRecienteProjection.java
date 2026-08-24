package com.uteq.backend.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface PagoRecienteProjection {
    Long getMultaId();
    BigDecimal getMontoPagado();
    Instant getFechaPagada();
    String getUsuarioCorreo();
    String getUsuarioNombre();
    String getLibroTitulo();
}
