package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

public interface ReporteCategoriasDemandadasProjection {

    Integer getCategoriaId();

    String getCategoriaNombre();

    Long getTotalPrestamos();

    BigDecimal getPorcentaje();
}
