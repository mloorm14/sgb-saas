package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import com.uteq.backend.repository.projection.MultaPendientePorPrestamoProjection;
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
 * {@link MultaProcedureRepository}.
 */
@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {

    // Multa no tiene usuarioId propio (a propósito, ver Javadoc de Multa/
    // Prestamo: sin @ManyToOne entre entidades para mantener el CRUD libre
    // de joins). Se resuelve acá con un "ad hoc join" JPQL (JOIN ... ON,
    // soportado por Hibernate 6) contra Prestamo, ya que sí hace falta
    // filtrar por usuario para el endpoint GET /multas/usuario/{id}.
    @Query("SELECT m FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId WHERE p.usuarioId = :usuarioId")
    Page<Multa> findByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);

    // Saldo total pendiente (monto - monto_pagado) por usuario:
    // alimenta la tarjeta "Total Pendiente" del módulo de gestión de multas.
    @Query("SELECT COALESCE(SUM(m.monto - m.montoPagado), 0) FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId "
            + "WHERE p.usuarioId = :usuarioId AND m.estadoMultaId = :estadoMultaId")
    BigDecimal sumSaldoByUsuarioIdAndEstadoMultaId(@Param("usuarioId") Long usuarioId,
                                                   @Param("estadoMultaId") Integer estadoMultaId);

    // ── Módulo de préstamos (ventanilla) ─────────────────────
    // Las 3 consultas siguientes comparten el mismo join ad hoc de arriba.
    // estadoMultaId SIEMPRE llega resuelto por nombre desde
    // EstadoMultaRepository (PENDIENTE), nunca hardcodeado.

    // Monto total adeudado por el usuario: alimenta la tarjeta "Usuario
    // Bloqueado" del Caso C ("...multas pendientes de pago ($X.XX)") y la
    // condición de bloqueo (monto > 0).
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId "
            + "WHERE p.usuarioId = :usuarioId AND m.estadoMultaId = :estadoMultaId")
    BigDecimal sumMontoByUsuarioIdAndEstadoMultaId(@Param("usuarioId") Long usuarioId,
                                                   @Param("estadoMultaId") Integer estadoMultaId);

    // Cantidad de multas en un estado dado (para el texto explicativo del
    // Caso C y el badge de la tarjeta de usuario).
    @Query("SELECT COUNT(m) FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId "
            + "WHERE p.usuarioId = :usuarioId AND m.estadoMultaId = :estadoMultaId")
    long countByUsuarioIdAndEstadoMultaId(@Param("usuarioId") Long usuarioId,
                                          @Param("estadoMultaId") Integer estadoMultaId);

    // Multas pendientes agrupadas por préstamo: permite marcar en el
    // historial qué préstamos devueltos tarde arrastran multa sin pagar,
    // con una sola consulta para toda la lista (sin N+1).
    @Query("SELECT m.prestamoId AS prestamoId, SUM(m.monto) AS totalPendiente "
            + "FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId "
            + "WHERE p.usuarioId = :usuarioId AND m.estadoMultaId = :estadoMultaId "
            + "GROUP BY m.prestamoId")
    List<MultaPendientePorPrestamoProjection> findPendientesAgrupadasPorPrestamo(
            @Param("usuarioId") Long usuarioId, @Param("estadoMultaId") Integer estadoMultaId);

    // Usuarios con al menos una multa pendiente (batch): evita N+1 en
    // UsuarioAdminService.toListadoDTO() al consultar una sola vez para
    // todos los usuarios de la página.
    @Query("SELECT DISTINCT p.usuarioId FROM Multa m JOIN Prestamo p ON p.id = m.prestamoId "
            + "WHERE p.usuarioId IN :usuarioIds AND m.estadoMultaId = :estadoMultaId")
    List<Long> findUsuarioIdsConMultasPendientes(@Param("usuarioIds") List<Long> usuarioIds,
                                                  @Param("estadoMultaId") Integer estadoMultaId);
}