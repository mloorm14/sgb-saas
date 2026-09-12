package com.uteq.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.StoredProcedureQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
class FineProcedureRepositoryCustomImpl implements FineProcedureRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<String, Object> spPayFineProcedure(Long fineId) {
        StoredProcedureQuery spq = em.createStoredProcedureQuery("sp_pagar_multa");
        spq.registerStoredProcedureParameter(1, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(2, Long.class, ParameterMode.OUT);
        spq.registerStoredProcedureParameter(3, Boolean.class, ParameterMode.OUT);
        spq.setParameter(1, fineId);
        spq.execute();
        Map<String, Object> result = new HashMap<>();
        result.put("o_multa_id", spq.getOutputParameterValue(2));
        result.put("o_usuario_desbloqueado", spq.getOutputParameterValue(3));
        return result;
    }

    @Override
    public Map<String, Object> spVoidFineProcedure(Long fineId, String reason, String roleExecutor) {
        StoredProcedureQuery spq = em.createStoredProcedureQuery("sp_anular_multa");
        spq.registerStoredProcedureParameter(1, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(2, String.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(4, Long.class, ParameterMode.OUT);
        spq.registerStoredProcedureParameter(5, Boolean.class, ParameterMode.OUT);
        spq.setParameter(1, fineId);
        spq.setParameter(2, reason);
        spq.setParameter(3, roleExecutor);
        spq.execute();
        Map<String, Object> result = new HashMap<>();
        result.put("o_multa_id", spq.getOutputParameterValue(4));
        result.put("o_usuario_desbloqueado", spq.getOutputParameterValue(5));
        return result;
    }
}
