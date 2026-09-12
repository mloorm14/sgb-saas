package com.uteq.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
class FineProcedureRepositoryCustomImpl implements FineProcedureRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<String, Object> spPayFineProcedure(Long fineId) {
        Query q = em.createNativeQuery("SELECT * FROM sp_pagar_multa(?1)");
        q.setParameter(1, fineId);
        Object[] row = (Object[]) q.getSingleResult();
        
        Map<String, Object> result = new HashMap<>();
        result.put("o_multa_id", ((Number) row[0]).longValue());
        result.put("o_usuario_desbloqueado", (Boolean) row[1]);
        return result;
    }

    @Override
    public Map<String, Object> spVoidFineProcedure(Long fineId, String reason, String roleExecutor) {
        Query q = em.createNativeQuery("SELECT * FROM sp_anular_multa(?1, ?2, ?3)");
        q.setParameter(1, fineId);
        q.setParameter(2, reason);
        q.setParameter(3, roleExecutor);
        Object[] row = (Object[]) q.getSingleResult();
        
        Map<String, Object> result = new HashMap<>();
        result.put("o_multa_id", ((Number) row[0]).longValue());
        result.put("o_usuario_desbloqueado", (Boolean) row[1]);
        return result;
    }
}
