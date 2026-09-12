package com.uteq.backend.repository.projection;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_reporte_libros_mas_prestados} (db/procs/). Los nombres de los
 * getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface BookMostLoanedProjection {

    Long getBookId();

    String getTitle();

    String getIsbn();

    Long getTotalLoans();
}
