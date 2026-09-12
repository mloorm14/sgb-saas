package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Invocación del procedimiento de db/procs/ relacionado con reservaciones.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface ReservationProcedureRepository extends Repository<Reservation, Long> {

    @Procedure(procedureName = "sp_expirar_reservaciones_vencidas")
    Integer spExpireReservationsVencidasProcedure();

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    /**
     * sp_expirar_reservaciones_vencidas: retorno escalar único (INTEGER).
     * El objeto es FUNCTION, no PROCEDURE nativo (por eso no usa
     * {@code @Procedure}): @Query nativa con "SELECT sp_expirar_reservaciones_vencidas()"
     * (sin argumentos en el SQL) omite el parámetro por completo, así que
     * Postgres aplica igual su DEFAULT NOW() -- mismo comportamiento de antes.
     */
    @Query(value = "SELECT sp_expirar_reservaciones_vencidas()", nativeQuery = true)
    Integer spExpireReservationsVencidas();

    /**
     * Misma función, variante para pruebas: permite fijar p_ahora en vez
     * de depender del valor por defecto NOW() de PostgreSQL. Aquí el
     * parámetro SÍ se envía explícito en el SQL (a diferencia del método
     * sin argumentos de arriba), así que el DEFAULT de Postgres no aplica.
     */
    @Query(value = "SELECT sp_expirar_reservaciones_vencidas(:p_ahora)", nativeQuery = true)
    Integer spExpireReservationsVencidas(@Param("p_ahora") OffsetDateTime ahora);
}