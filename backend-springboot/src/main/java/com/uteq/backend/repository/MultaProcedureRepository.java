package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.jpa.repository.query.Procedure; -- ya no se
// usa, ver bloques comentados abajo (mismo fallo de sintaxis "=>" que
// PrestamoProcedureRepository -- ver
// docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md).
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Map;

/**
 * Invocación de los procedimientos de db/procs/ relacionados con multas.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface MultaProcedureRepository extends Repository<Multa, Long> {

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * sp_pagar_multa: 2 parámetros OUT (o_multa_id, o_usuario_desbloqueado).
    //  * Resuelto vía @NamedStoredProcedureQuery declarado en la entidad
    //  * {@link Multa} (name = "Multa.pagarMulta") — @Procedure con
    //  * procedureName directo no soporta bien múltiples OUT en PostgreSQL.
    //  * Retorna Map<String,Object> con una entrada por parámetro OUT.
    //  */
    // @Procedure(name = "Multa.pagarMulta")
    // Map<String, Object> spPagarMulta(@Param("p_multa_id") Long multaId);

    /**
     * sp_pagar_multa: 2 parámetros OUT (o_multa_id, o_usuario_desbloqueado).
     * Antes resuelto vía @NamedStoredProcedureQuery en {@link Multa} (ver
     * bloque comentado arriba) -- fallaba con el mismo error de sintaxis
     * "=>" que sp_crear_prestamo. @Query nativa con "SELECT * FROM ..."
     * expande los OUT params en columnas del Map<String,Object> resultante.
     */
    @Query(value = "SELECT * FROM sp_pagar_multa(:p_multa_id)", nativeQuery = true)
    Map<String, Object> spPagarMulta(@Param("p_multa_id") Long multaId);

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * sp_anular_multa: 2 parámetros OUT, misma resolución que
    //  * spPagarMulta vía @NamedStoredProcedureQuery (name = "Multa.anularMulta").
    //  */
    // @Procedure(name = "Multa.anularMulta")
    // Map<String, Object> spAnularMulta(
    //         @Param("p_multa_id") Long multaId,
    //         @Param("p_motivo") String motivo,
    //         @Param("p_rol_ejecutor") String rolEjecutor
    // );

    /**
     * sp_anular_multa: 3 IN + 2 OUT. Mismo cambio de mecanismo que
     * spPagarMulta, por el mismo fallo documentado.
     */
    @Query(value = "SELECT * FROM sp_anular_multa(:p_multa_id, :p_motivo, :p_rol_ejecutor)", nativeQuery = true)
    Map<String, Object> spAnularMulta(
            @Param("p_multa_id") Long multaId,
            @Param("p_motivo") String motivo,
            @Param("p_rol_ejecutor") String rolEjecutor
    );
}