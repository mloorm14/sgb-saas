package com.uteq.backend.repository;

import java.util.Map;

/**
 * Fragmento custom para stored procedures de prestamos.
 * Implementado en LoanProcedureRepositoryCustomImpl con EntityManager
 * y binding posicional para evitar sintaxis nombre => ? generada por Hibernate 6.
 */
public interface LoanProcedureRepositoryCustom {
    Long spCreateLoanProcedure(Long userId, Long bookId, Long librarianId, Integer daysLoan);
    Map<String, Object> spRegisterLoanReturn(Long loanId);
}
