package com.uteq.backend.repository.projection;

import java.time.Instant;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_listar_prestamos_activos_por_usuario} (db/procs/). Los nombres
 * de los getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface LoanActiveProjection {

    Long getLoanId();

    String getBookTitle();

    String getBookIsbn();

    Instant getDateLoan();

    Instant getDateLoanReturnEstimada();

    Integer getDaysRestantes();

    String getStatusName();
}
