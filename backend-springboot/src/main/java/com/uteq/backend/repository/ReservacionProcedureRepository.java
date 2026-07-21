package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Invocación del procedimiento de db/procs/ relacionado con reservaciones.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface ReservacionProcedureRepository extends Repository<Reservacion, Long> {

    /**
     * sp_expirar_reservaciones_vencidas: retorno escalar único (INTEGER) —
     * caso simple, mapea directo con @Procedure. Usa el parámetro por
     * defecto de la función (p_ahora = NOW()) al no enviar argumento.
     */
    @Procedure(procedureName = "sp_expirar_reservaciones_vencidas")
    Integer spExpirarReservacionesVencidas();

    /**
     * Misma función, variante para pruebas: permite fijar p_ahora en vez
     * de depender del valor por defecto NOW() de PostgreSQL.
     */
    @Procedure(procedureName = "sp_expirar_reservaciones_vencidas")
    Integer spExpirarReservacionesVencidas(@Param("p_ahora") OffsetDateTime ahora);
}
