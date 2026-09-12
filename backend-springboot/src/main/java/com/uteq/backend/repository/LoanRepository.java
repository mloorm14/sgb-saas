package com.uteq.backend.repository;

import com.uteq.backend.entity.Loan;
import com.uteq.backend.repository.projection.LoanActiveProjection;
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
 * libros/estados vive en {@link LoanProcedureRepository}
 * (fn_listar_prestamos_activos_por_usuario, fn_reporte_libros_mas_prestados).
 */
@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByUserId(Long userId, Pageable pageable);

    Page<Loan> findByStatusLoanId(Integer statusId, Pageable pageable);

    // Usada por NotificacionVencimientoScheduler: préstamos vigentes
    // (ACTIVO/RENOVADO -- estadoIds ya resueltos por el llamador, ver
    // EstadoPrestamoRepository) cuya fecha_devolucion_estimada cae dentro
    // de la ventana [ahora, ahora + minutos de anticipación configurados].
    List<Loan> findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(
            List<Integer> statusLoanIds, OffsetDateTime from, OffsetDateTime until);

    // Historial reciente del usuario, más nuevo primero (línea de tiempo acotada, sin paginación).
    List<Loan> findByUserIdOrderByIdDesc(Long userId);

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
            + "WHERE p.usuario_id = :userId "
            + "AND ep.nombre <> 'DEVUELTO' "
            + "ORDER BY p.fecha_devolucion_estimada ASC", nativeQuery = true)
    List<LoanActiveProjection> findActivesByUserId(@Param("userId") Long userId);
}
