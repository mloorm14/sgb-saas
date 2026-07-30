package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD elemental sobre {@code reservaciones}. La expiración masiva vive
 * en {@link ReservacionProcedureRepository#spExpirarReservacionesVencidas()}.
 */
@Repository
public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {

    Page<Reservacion> findByUsuarioId(Long usuarioId, Pageable pageable);
}
