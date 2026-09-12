package com.uteq.backend.repository;

import java.util.Map;

/**
 * Fragmento custom para stored procedures de multas.
 */
public interface FineProcedureRepositoryCustom {
    Map<String, Object> spPayFineProcedure(Long fineId);
    Map<String, Object> spVoidFineProcedure(Long fineId, String reason, String roleExecutor);
}
