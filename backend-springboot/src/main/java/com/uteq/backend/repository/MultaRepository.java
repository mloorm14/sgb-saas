package com.uteq.backend.repository;

import com.uteq.backend.entity.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD elemental sobre {@code multas}. Sin métodos derivados adicionales
 * por ahora — se agregan cuando el servicio los necesite. Pagar/anular
 * viven en {@link MultaProcedureRepository}.
 */
@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {
}
