package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD elemental sobre {@code reservaciones}. Sin métodos derivados
 * adicionales por ahora — se agregan cuando el servicio los necesite.
 * La expiración masiva vive en
 * {@link ReservacionProcedureRepository#spExpirarReservacionesVencidas()}.
 */
@Repository
public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {
}
