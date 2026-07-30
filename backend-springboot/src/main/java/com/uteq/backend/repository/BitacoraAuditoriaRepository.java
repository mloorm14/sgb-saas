package com.uteq.backend.repository;

import com.uteq.backend.entity.BitacoraAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD elemental sobre {@code bitacora_auditoria}. Solo se usa {@code save()}
 * por ahora (INSERT de un evento de auditoría) -- no hay pantalla de
 * consulta de bitácora todavía, así que no se agregan métodos derivados
 * hasta que exista un caso de uso real que los necesite.
 */
@Repository
public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Long> {
}
