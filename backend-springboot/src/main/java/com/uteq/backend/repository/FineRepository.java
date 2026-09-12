package com.uteq.backend.repository;

import com.uteq.backend.entity.Fine;
import com.uteq.backend.repository.projection.FinePendingByLoanProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * CRUD elemental sobre {@code multas}. Pagar/anular viven en
 * {@link FineProcedureRepository}.
 */
@Repository
public interface FineRepository extends JpaRepository<Fine, Long> {

    // Multa no tiene usuarioId propio (a propósito, ver Javadoc de Multa/
    // Prestamo: sin @ManyToOne entre entidades para mantener el CRUD libre
    // de joins). Se resuelve acá con un "ad hoc join" JPQL (JOIN ... ON,
    // soportado por Hibernate 6) contra Prestamo, ya que sí hace falta
    // filtrar por usuario para el endpoint GET /multas/usuario/{id}.
    @Query("SELECT m FROM Fine m JOIN Loan p ON p.id = m.loanId WHERE p.userId = :userId")
    Page<Fine> findByUserId(@Param("userId") Long userId, Pageable pageable);

    // Saldo total pendiente (monto - monto_pagado) por usuario:
    // alimenta la tarjeta "Total Pendiente" del módulo de gestión de multas.
    @Query("SELECT COALESCE(SUM(m.amount - m.amountPaid), 0) FROM Fine m JOIN Loan p ON p.id = m.loanId "
            + "WHERE p.userId = :userId AND m.statusFineId = :statusFineId")
    BigDecimal sumBalanceByUserIdAndStatusFineId(@Param("userId") Long userId,
                                                   @Param("statusFineId") Integer statusFineId);

    // Consultas de ventanilla (mismo join ad hoc a Prestamo; estado resuelto por nombre).

    // Monto total adeudado por el usuario: alimenta la tarjeta "Usuario
    // Bloqueado" del Caso C ("...multas pendientes de pago ($X.XX)") y la
    // condición de bloqueo (monto > 0).
    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM Fine m JOIN Loan p ON p.id = m.loanId "
            + "WHERE p.userId = :userId AND m.statusFineId = :statusFineId")
    BigDecimal sumAmountByUserIdAndStatusFineId(@Param("userId") Long userId,
                                                   @Param("statusFineId") Integer statusFineId);

    // Cantidad de multas en un estado dado (para el texto explicativo del
    // Caso C y el badge de la tarjeta de usuario).
    @Query("SELECT COUNT(m) FROM Fine m JOIN Loan p ON p.id = m.loanId "
            + "WHERE p.userId = :userId AND m.statusFineId = :statusFineId")
    long countByUserIdAndStatusFineId(@Param("userId") Long userId,
                                          @Param("statusFineId") Integer statusFineId);

    // Multas pendientes agrupadas por préstamo: permite marcar en el
    // historial qué préstamos devueltos tarde arrastran multa sin pagar,
    // con una sola consulta para toda la lista (sin N+1).
    @Query("SELECT m.loanId AS loanId, SUM(m.amount) AS totalPending "
            + "FROM Fine m JOIN Loan p ON p.id = m.loanId "
            + "WHERE p.userId = :userId AND m.statusFineId = :statusFineId "
            + "GROUP BY m.loanId")
    List<FinePendingByLoanProjection> findPendientesGroupedsByLoan(
            @Param("userId") Long userId, @Param("statusFineId") Integer statusFineId);

    // Usuarios con al menos una multa pendiente (batch): evita N+1 en
    // UsuarioAdminService.toListadoDTO() al consultar una sola vez para
    // todos los usuarios de la página.
    @Query("SELECT DISTINCT p.userId FROM Fine m JOIN Loan p ON p.id = m.loanId "
            + "WHERE p.userId IN :userIds AND m.statusFineId = :statusFineId")
    List<Long> findUserIdsWithFinesPendientes(@Param("userIds") List<Long> userIds,
                                                  @Param("statusFineId") Integer statusFineId);
}