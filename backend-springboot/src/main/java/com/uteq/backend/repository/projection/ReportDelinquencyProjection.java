package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_reporte_indice_morosidad} (db/procs/). Los nombres de los
 * getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface ReportDelinquencyProjection {

    Long getUserId();

    String getName();

    String getLastName();

    String getEmail();

    BigDecimal getAmountTotalAdeudado();

    Long getQuantityFinesPendientes();

    BigDecimal getDaysAtrasoPromedio();
}
