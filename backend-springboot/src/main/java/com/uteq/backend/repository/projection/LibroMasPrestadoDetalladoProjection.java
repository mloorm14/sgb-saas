package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

public interface LibroMasPrestadoDetalladoProjection {

    Long getLibroId();

    String getTitulo();

    String getIsbn();

    String getAutorNombre();

    String getCategoriaNombre();

    Long getTotalPrestamos();

    BigDecimal getPorcentaje();
}
