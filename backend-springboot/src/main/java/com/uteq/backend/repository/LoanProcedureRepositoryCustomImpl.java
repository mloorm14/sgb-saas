package com.uteq.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.StoredProcedureQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Repository
class LoanProcedureRepositoryCustomImpl implements LoanProcedureRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Long spCreateLoanProcedure(Long userId, Long bookId, Long librarianId, Integer daysLoan) {
        StoredProcedureQuery spq = em.createStoredProcedureQuery("sp_crear_prestamo");
        spq.registerStoredProcedureParameter(1, Long.class, ParameterMode.OUT);
        spq.registerStoredProcedureParameter(2, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(3, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(4, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(5, Integer.class, ParameterMode.IN);
        spq.setParameter(2, userId);
        spq.setParameter(3, bookId);
        spq.setParameter(4, librarianId);
        spq.setParameter(5, daysLoan);
        spq.execute();
        return ((Number) spq.getOutputParameterValue(1)).longValue();
    }

    @Override
    public Map<String, Object> spRegisterLoanReturn(Long loanId) {
        StoredProcedureQuery spq = em.createStoredProcedureQuery("sp_registrar_devolucion");
        spq.registerStoredProcedureParameter(1, Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter(2, Long.class, ParameterMode.OUT);
        spq.registerStoredProcedureParameter(3, Boolean.class, ParameterMode.OUT);
        spq.registerStoredProcedureParameter(4, BigDecimal.class, ParameterMode.OUT);
        spq.setParameter(1, loanId);
        spq.execute();
        Map<String, Object> result = new HashMap<>();
        result.put("o_prestamo_id", spq.getOutputParameterValue(2));
        result.put("o_hubo_multa", spq.getOutputParameterValue(3));
        result.put("o_monto_multa", spq.getOutputParameterValue(4));
        return result;
    }
}
