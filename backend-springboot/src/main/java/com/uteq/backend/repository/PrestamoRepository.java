package com.uteq.backend.repository;

import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CRUD elemental sobre {@code prestamos}. Solo consultas derivadas de una
 * sola tabla (sin joins) — cualquier lectura que combine préstamos con
 * libros/estados vive en {@link PrestamoProcedureRepository}
 * (fn_listar_prestamos_activos_por_usuario, fn_reporte_libros_mas_prestados).
 */
@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    Page<Prestamo> findByUsuarioId(Long usuarioId, Pageable pageable);

    Page<Prestamo> findByEstadoPrestamoId(Integer estadoId, Pageable pageable);

    // Usada por NotificacionVencimientoScheduler: préstamos vigentes
    // (ACTIVO/RENOVADO -- estadoIds ya resueltos por el llamador, ver
    // EstadoPrestamoRepository) cuya fecha_devolucion_estimada cae dentro
    // de la ventana [ahora, ahora + minutos de anticipación configurados].
    List<Prestamo> findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(
            List<Integer> estadoPrestamoIds, OffsetDateTime desde, OffsetDateTime hasta);

    // Módulo de préstamos (ventanilla): historial reciente del usuario,
    // más nuevo primero. Sin paginación a propósito -- el frontend muestra
    // una línea de tiempo acotada (el service recorta al tope definido),
    // no una tabla paginada.
    List<Prestamo> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    // Préstamos activos del LECTOR: misma lógica que
    // fn_listar_prestamos_activos_por_usuario pero como query nativa JPA
    // para evitar dependencia del stored procedure en producción.
    @Query(value = "SELECT p.id AS prestamo_id, l.titulo AS libro_titulo, l.isbn AS libro_isbn, "
            + "p.fecha_prestamo, p.fecha_devolucion_estimada, "
            + "(p.fecha_devolucion_estimada::date - NOW()::date)::INTEGER AS dias_restantes, "
            + "ep.nombre AS estado_nombre "
            + "FROM prestamos p "
            + "JOIN libros l ON l.id = p.libro_id "
            + "JOIN estados_prestamo ep ON ep.id = p.estado_prestamo_id "
            + "WHERE p.usuario_id = :usuarioId "
            + "AND ep.nombre <> 'DEVUELTO' "
            + "ORDER BY p.fecha_devolucion_estimada ASC", nativeQuery = true)
    List<PrestamoActivoProjection> findActivosByUsuarioId(@Param("usuarioId") Long usuarioId);
}
