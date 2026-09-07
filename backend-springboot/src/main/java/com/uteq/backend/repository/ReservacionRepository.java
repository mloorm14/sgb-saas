package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.repository.projection.ReservacionHoyProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CRUD elemental sobre {@code reservaciones}. La expiración masiva vive
 * en {@link ReservacionProcedureRepository#spExpirarReservacionesVencidas()}.
 */
@Repository
public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {

    Page<Reservacion> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Usado por PrestamoService.renovar(): una renovación se bloquea si OTRO
    // usuario (usuarioId <> el dueño del préstamo) tiene una reserva vigente
    // sobre el mismo libro. "Vigente" = no RETIRADA/EXPIRADA/CANCELADA, ver
    // los ids que arma PrestamoService a partir de EstadoReservacionRepository.
    boolean existsByLibroIdAndEstadoReservacionIdInAndUsuarioIdNot(
            Long libroId, List<Integer> estadosReservacionIds, Long usuarioId);

    // Reservaciones a expirar en la corrida actual (mismo filtro que la función masiva, para notificar antes del UPDATE).
    List<Reservacion> findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(
            List<Integer> estadosReservacionIds, OffsetDateTime ahora);

    // Reserva vigente más reciente del usuario (la que se convierte en préstamo).
    Optional<Reservacion> findFirstByUsuarioIdAndEstadoReservacionIdInOrderByFechaReservaDesc(
            Long usuarioId, List<Integer> estadosReservacionIds);

    // Conteo de reservas vigentes del usuario (badge de activas).
    long countByUsuarioIdAndEstadoReservacionIdIn(
            Long usuarioId, List<Integer> estadosReservacionIds);

    // Dashboard del bibliotecario: reservaciones cuya fecha límite de
    // retiro cae HOY, con libro/usuario ya resueltos (evita el N+1 que
    // tendría el frontend pidiendo cada libro/usuario por separado para
    // un widget que se carga en cada visita al dashboard).
    @Query(value = """
        SELECT r.id AS reservacionId,
               u.nombre || ' ' || u.apellido AS usuarioNombre,
               u.correo AS usuarioCorreo,
               l.titulo AS libroTitulo,
               l.isbn AS libroIsbn,
               er.nombre AS estadoNombre,
               r.fecha_limite_retiro AS fechaLimiteRetiro
        FROM reservaciones r
        JOIN usuarios u ON u.id = r.usuario_id
        JOIN libros l ON l.id = r.libro_id
        JOIN estados_reservacion er ON er.id = r.estado_reservacion_id
        WHERE r.fecha_limite_retiro >= CURRENT_DATE
          AND r.fecha_limite_retiro < CURRENT_DATE + INTERVAL '1 day'
          AND er.nombre IN ('PENDIENTE', 'LISTA_PARA_RETIRO')
        ORDER BY r.fecha_limite_retiro ASC
        """, nativeQuery = true)
    List<ReservacionHoyProjection> buscarReservacionesDeHoy();

    @Query(value = """
        SELECT r.id AS reservacionId,
               u.nombre || ' ' || u.apellido AS usuarioNombre,
               u.correo AS usuarioCorreo,
               l.titulo AS libroTitulo,
               l.isbn AS libroIsbn,
               er.nombre AS estadoNombre,
               r.fecha_limite_retiro AS fechaLimiteRetiro
        FROM reservaciones r
        JOIN usuarios u ON u.id = r.usuario_id
        JOIN libros l ON l.id = r.libro_id
        JOIN estados_reservacion er ON er.id = r.estado_reservacion_id
        WHERE r.fecha_limite_retiro >= CURRENT_DATE + INTERVAL '1 day'
          AND er.nombre IN ('PENDIENTE', 'LISTA_PARA_RETIRO')
        ORDER BY r.fecha_limite_retiro ASC
        """, nativeQuery = true)
    List<ReservacionHoyProjection> buscarReservacionesProximas();
}
