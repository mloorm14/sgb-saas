package com.uteq.backend.repository.projection;

public interface ReporteInventarioProjection {

    Long getLibroId();

    String getTitulo();

    String getIsbn();

    String getAutorNombre();

    String getCategoriaNombre();

    String getEditorialNombre();

    String getProveedorNombre();

    String getIdiomaNombre();

    String getEstadoLibroNombre();

    Short getAnioPublicacion();

    String getUbicacionFisica();

    Short getStockTotal();

    Short getStockDisponible();

    String getEstadoDisponibilidad();
}
