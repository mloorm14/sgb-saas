package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
