package com.uteq.backend.repository.projection;

import java.time.OffsetDateTime;

/**
 * Proyección de una fila retornada por la función SQL
 * {@code fn_listar_prestamos_activos_por_usuario} (db/procs/). Los nombres
 * de los getters (relajados a snake_case) deben coincidir con las columnas
 * declaradas en el {@code RETURNS TABLE} de esa función.
 */
public interface PrestamoActivoProjection {

    Long getPrestamoId();

    String getLibroTitulo();

    String getLibroIsbn();

    OffsetDateTime getFechaPrestamo();

    OffsetDateTime getFechaDevolucionEstimada();

    Integer getDiasRestantes();

    String getEstadoNombre();
}
