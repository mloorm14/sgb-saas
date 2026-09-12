package com.uteq.backend.repository;

import java.util.Map;

/**
 * Fragmento custom para stored procedures de prestamos.
 * Implementado en LoanProcedureRepositoryCustomImpl con EntityManager
 * y binding posicional (Integer) para evitar sintaxis nombre => ?
 * de Hibernate 6 que pgjdbc rechaza en {call ...}.
 */
public interface LoanProcedureRepositoryCustom {
    @org.springframework.data.jpa.repository.query.Procedure(procedureName = "sp_crear_prestamo")`n    Long spCreateLoanProcedure(Long userId, Long bookId, Long librarianId, Integer daysLoan);
    Map<String, Object> spRegisterLoanReturn(Long loanId);
}
