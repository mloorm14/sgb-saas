package com.uteq.backend.repository.projection;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_reporte_libros_mas_prestados} (db/procs/). Los nombres de los
 * getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface LibroMasPrestadoProjection {

    Long getLibroId();

    String getTitulo();

    String getIsbn();

    Long getTotalPrestamos();
}
