package com.uteq.backend.repository;

import com.uteq.backend.entity.Reservacion;
import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.jpa.repository.query.Procedure; -- ya no
// se usa, ver bloques comentados abajo. Este era el caso de la Falla 1
// documentada en
// docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md:
// Hibernate generaba "call sp_expirar_reservaciones_vencidas(...)" nativo,
// que Postgres rechaza con "is not a procedure" porque el objeto es
// FUNCTION, no PROCEDURE.
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Invocación del procedimiento de db/procs/ relacionado con reservaciones.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface ReservacionProcedureRepository extends Repository<Reservacion, Long> {

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * sp_expirar_reservaciones_vencidas: retorno escalar único (INTEGER) —
    //  * caso simple, mapea directo con @Procedure. Usa el parámetro por
    //  * defecto de la función (p_ahora = NOW()) al no enviar argumento.
    //  */
    // @Procedure(procedureName = "sp_expirar_reservaciones_vencidas")
    // Integer spExpirarReservacionesVencidas();

    /**
     * sp_expirar_reservaciones_vencidas: retorno escalar único (INTEGER).
     * Antes usaba @Procedure (ver bloque comentado arriba) -- fallaba con
     * "sp_expirar_reservaciones_vencidas(unknown) is not a procedure"
     * porque el objeto es FUNCTION, no PROCEDURE nativo. @Query nativa con
     * "SELECT sp_expirar_reservaciones_vencidas()" (sin argumentos en el
     * SQL) sigue omitiendo el parámetro por completo, así que Postgres
     * aplica igual su DEFAULT NOW() -- mismo comportamiento de antes.
     */
    @Query(value = "SELECT sp_expirar_reservaciones_vencidas()", nativeQuery = true)
    Integer spExpirarReservacionesVencidas();

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * Misma función, variante para pruebas: permite fijar p_ahora en vez
    //  * de depender del valor por defecto NOW() de PostgreSQL.
    //  */
    // @Procedure(procedureName = "sp_expirar_reservaciones_vencidas")
    // Integer spExpirarReservacionesVencidas(@Param("p_ahora") OffsetDateTime ahora);

    /**
     * Misma función, variante para pruebas: permite fijar p_ahora en vez
     * de depender del valor por defecto NOW() de PostgreSQL. Aquí el
     * parámetro SÍ se envía explícito en el SQL (a diferencia del método
     * sin argumentos de arriba), así que el DEFAULT de Postgres no aplica
     * -- comportamiento sin cambios respecto a la versión anterior.
     */
    @Query(value = "SELECT sp_expirar_reservaciones_vencidas(:p_ahora)", nativeQuery = true)
    Integer spExpirarReservacionesVencidas(@Param("p_ahora") OffsetDateTime ahora);
}