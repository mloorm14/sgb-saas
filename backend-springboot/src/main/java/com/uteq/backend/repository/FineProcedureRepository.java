package com.uteq.backend.repository;

import com.uteq.backend.entity.Fine;
import com.uteq.backend.repository.projection.RecentPaymentProjection;
import com.uteq.backend.repository.projection.SummaryFinancialFinesProjection;
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
public interface FineProcedureRepository extends Repository<Fine, Long> {

    // NOTA: sin @Param — binding posicional para evitar sintaxis "nombre => ?" de Hibernate 6
    @Procedure(name = "Multa.pagarMulta")
    Map<String, Object> spPayFineProcedure(Long fineId);

    /**
     * sp_pagar_multa: 2 parámetros OUT (o_multa_id, o_usuario_desbloqueado).
     * Antes resuelto vía {@code @NamedStoredProcedureQuery} en {@link Fine} (ver
     * bloque comentado arriba) -- fallaba con el mismo error de sintaxis
     * "=>" que sp_crear_prestamo. @Query nativa con "SELECT * FROM ..."
     * expande los OUT params en columnas del {@code Map<String,Object>} resultante.
     */
    @Query(value = "SELECT * FROM sp_pagar_multa(:p_multa_id)", nativeQuery = true)
    Map<String, Object> spPayFine(@Param("p_multa_id") Long fineId);

    // NOTA: sin @Param — binding posicional para evitar sintaxis "nombre => ?" de Hibernate 6
    @Procedure(name = "Multa.anularMulta")
    Map<String, Object> spVoidFineProcedure(Long fineId, String reason, String roleExecutor);

    /**
     * sp_anular_multa: 3 IN + 2 OUT. Mismo cambio de mecanismo que
     * spPagarMulta, por el mismo fallo documentado.
     */
    @Query(value = "SELECT * FROM sp_anular_multa(:p_multa_id, :p_motivo, :p_rol_ejecutor)", nativeQuery = true)
    Map<String, Object> spVoidFine(
            @Param("p_multa_id") Long fineId,
            @Param("p_motivo") String reason,
            @Param("p_rol_ejecutor") String roleExecutor
    );

    /**
     * sp_pago_parcial_multa: acumula un pago parcial en monto_pagado.
     * 4 OUT: o_multa_id, o_estado ('PAGADA'|'PENDIENTE'),
     * o_saldo_restante, o_usuario_desbloqueado.
     */
    @Query(value = "SELECT * FROM sp_pago_parcial_multa(:p_multa_id, :p_monto_pagado)", nativeQuery = true)
    Map<String, Object> spPaymentParcialFine(
            @Param("p_multa_id") Long fineId,
            @Param("p_monto_pagado") java.math.BigDecimal amountPaid
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
    SummaryFinancialFinesProjection fnReportSummaryFinancial(
            @Param("p_desde") OffsetDateTime from,
            @Param("p_hasta") OffsetDateTime until
    );

    @Query(value = "SELECT * FROM fn_pagos_recientes(:p_limit)", nativeQuery = true)
    java.util.List<RecentPaymentProjection> fnPaymentsRecientes(
            @Param("p_limit") Integer limit
    );
}
