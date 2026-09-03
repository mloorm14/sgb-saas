package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import com.uteq.backend.repository.projection.PagoRecienteProjection;
import com.uteq.backend.repository.projection.ResumenFinancieroMultasProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Invocación de los procedimientos de db/procs/ relacionados con multas.
 * Repositorio "solo procedimientos" (no extiende JpaRepository).
 */
@org.springframework.stereotype.Repository
public interface MultaProcedureRepository extends Repository<Multa, Long> {

    @Procedure(name = "Multa.pagarMulta")
    Map<String, Object> spPagarMultaProcedure(@Param("p_multa_id") Long multaId);

    /**
     * sp_pagar_multa: 2 parámetros OUT (o_multa_id, o_usuario_desbloqueado).
     * Antes resuelto vía @NamedStoredProcedureQuery en {@link Multa} (ver
     * bloque comentado arriba) -- fallaba con el mismo error de sintaxis
     * "=>" que sp_crear_prestamo. @Query nativa con "SELECT * FROM ..."
     * expande los OUT params en columnas del Map<String,Object> resultante.
     */
    @Query(value = "SELECT * FROM sp_pagar_multa(:p_multa_id)", nativeQuery = true)
    Map<String, Object> spPagarMulta(@Param("p_multa_id") Long multaId);

    @Procedure(name = "Multa.anularMulta")
    Map<String, Object> spAnularMultaProcedure(
            @Param("p_multa_id") Long multaId,
            @Param("p_motivo") String motivo,
            @Param("p_rol_ejecutor") String rolEjecutor
    );

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

    /**
     * sp_pago_parcial_multa: acumula un pago parcial en monto_pagado.
     * 4 OUT: o_multa_id, o_estado ('PAGADA'|'PENDIENTE'),
     * o_saldo_restante, o_usuario_desbloqueado.
     */
    @Query(value = "SELECT * FROM sp_pago_parcial_multa(:p_multa_id, :p_monto_pagado)", nativeQuery = true)
    Map<String, Object> spPagoParcialMulta(
            @Param("p_multa_id") Long multaId,
            @Param("p_monto_pagado") java.math.BigDecimal montoPagado
    );

    /**
     * fn_reporte_resumen_financiero_multas: función SQL pura, RETURNS TABLE
     * pero siempre exactamente 1 fila (agregación sin GROUP BY, COALESCE
     * cubre el caso sin datos) -- por eso el tipo de retorno es la
     * proyección directa, no un List, a diferencia de
     * fnReporteIndiceMorosidad/fnReporteLibrosMasPrestados de
     * PrestamoProcedureRepository (que sí pueden traer 0..N filas).
     */
    @Query(value = "SELECT * FROM fn_reporte_resumen_financiero_multas(:p_desde, :p_hasta)", nativeQuery = true)
    ResumenFinancieroMultasProjection fnReporteResumenFinanciero(
            @Param("p_desde") OffsetDateTime desde,
            @Param("p_hasta") OffsetDateTime hasta
    );

    @Query(value = "SELECT * FROM fn_pagos_recientes(:p_limit)", nativeQuery = true)
    java.util.List<PagoRecienteProjection> fnPagosRecientes(
            @Param("p_limit") Integer limit
    );
}