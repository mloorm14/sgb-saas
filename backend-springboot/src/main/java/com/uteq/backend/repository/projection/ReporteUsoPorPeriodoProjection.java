package com.uteq.backend.repository.projection;

import java.time.OffsetDateTime;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_reporte_uso_por_periodo} (db/procs/). Los nombres de los
 * getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface ReporteUsoPorPeriodoProjection {

    OffsetDateTime getPeriodo();

    Long getTotalPrestamos();

    Long getTotalDevoluciones();
}
