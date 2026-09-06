package com.uteq.backend.repository.projection;



/**
 * Proyección de una fila retornada por la query nativa
 * {@code buscarReservacionesDeHoy()} en {@code ReservacionRepository}.
 * Resuelve libro/usuario en una sola query para evitar el N+1 que
 * tendría el frontend pidiendo cada libro/usuario por separado.
 */
public interface ReservacionHoyProjection {
    Long getReservacionId();
    String getUsuarioNombre();
    String getUsuarioCorreo();
    String getLibroTitulo();
    String getLibroIsbn();
    String getEstadoNombre();
    java.time.Instant getFechaLimiteRetiro();
}
