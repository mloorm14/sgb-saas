package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}