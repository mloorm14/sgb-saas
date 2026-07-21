package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Map;

/**
 * Invocación de los procedimientos de db/procs/ relacionados con multas.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface MultaProcedureRepository extends Repository<Multa, Long> {

    /**
     * sp_pagar_multa: 2 parámetros OUT (o_multa_id, o_usuario_desbloqueado).
     * Resuelto vía @NamedStoredProcedureQuery declarado en la entidad
     * {@link Multa} (name = "Multa.pagarMulta") — @Procedure con
     * procedureName directo no soporta bien múltiples OUT en PostgreSQL.
     * Retorna Map<String,Object> con una entrada por parámetro OUT.
     */
    @Procedure(name = "Multa.pagarMulta")
    Map<String, Object> spPagarMulta(@Param("p_multa_id") Long multaId);

    /**
     * sp_anular_multa: 2 parámetros OUT, misma resolución que
     * spPagarMulta vía @NamedStoredProcedureQuery (name = "Multa.anularMulta").
     */
    @Procedure(name = "Multa.anularMulta")
    Map<String, Object> spAnularMulta(
            @Param("p_multa_id") Long multaId,
            @Param("p_motivo") String motivo,
            @Param("p_rol_ejecutor") String rolEjecutor
    );
}
