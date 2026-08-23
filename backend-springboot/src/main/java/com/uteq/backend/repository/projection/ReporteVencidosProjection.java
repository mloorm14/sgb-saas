package com.uteq.backend.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface ReporteVencidosProjection {

    Long getPrestamoId();

    String getUsuarioNombre();

    String getUsuarioCorreo();

    String getLibroTitulo();

    String getLibroIsbn();

    Instant getFechaDevolucionEstimada();

    Long getDiasAtraso();

    BigDecimal getMontoMultaEstimada();
}
