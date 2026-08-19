package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Proyección de la única fila retornada por la función SQL
 * {@code fn_reporte_resumen_financiero_multas} (db/procs/). Los nombres de
 * los getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface ResumenFinancieroMultasProjection {

    BigDecimal getTotalRecaudado();

    BigDecimal getTotalPendiente();
}
