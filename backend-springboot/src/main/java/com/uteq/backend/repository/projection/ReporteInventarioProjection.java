package com.uteq.backend.repository.projection;

public interface ReporteInventarioProjection {

    Long getLibroId();

    String getTitulo();

    String getIsbn();

    String getAutorNombre();

    String getCategoriaNombre();

    Short getStockTotal();

    Short getStockDisponible();

    String getEstadoDisponibilidad();
}
