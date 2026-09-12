package com.uteq.backend.repository;

import com.uteq.backend.entity.Fine;
import com.uteq.backend.repository.projection.RecentPaymentProjection;
import com.uteq.backend.repository.projection.SummaryFinancialFinesProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.data.jpa.repository.query.Procedure;

@org.springframework.stereotype.Repository
public interface FineProcedureRepository extends Repository<Fine, Long>, FineProcedureRepositoryCustom {

    @Procedure(name = "Multa.pagarMulta")
    Map<String, Object> spPayFineProcedure(Long fineId);

    @Procedure(name = "Multa.anularMulta")
    Map<String, Object> spVoidFineProcedure(Long fineId, String reason, String roleExecutor);

    @Query(value = "SELECT * FROM sp_pago_parcial_multa(:p_multa_id, :p_monto_pagado)", nativeQuery = true)
    Map<String, Object> spPaymentParcialFine(
            @Param("p_multa_id") Long fineId,
            @Param("p_monto_pagado") java.math.BigDecimal amountPaid
    );

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
