package com.uteq.backend.repository;

import com.uteq.backend.entity.BitacoraAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * CRUD elemental sobre {@code bitacora_auditoria}, más el filtro paginado
 * que consume {@code AuditoriaService} (Módulo 6). Los 4 filtros son
 * opcionales e independientes entre sí (usuario, módulo, rango de fecha):
 * en vez de armar 2^4 variantes de {@code findBy...} derivado, se usa una
 * única @Query JPQL con "(:param IS NULL OR columna = :param)" por cada
 * filtro -- patrón estándar de Spring Data JPA para filtros combinables sin
 * Specification/Criteria API, que este proyecto no usa en ningún otro
 * repositorio (ver ADR-013: la estrategia de acceso a datos ya es
 * CRUD-ORM + SP, no se agrega una tercera vía solo para esto).
 */
@Repository
public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Long> {

    @Query("SELECT b FROM BitacoraAuditoria b WHERE "
            + "(:usuarioId IS NULL OR b.usuarioId = :usuarioId) AND "
            + "(:modulo IS NULL OR b.tablaAfectada = :modulo) AND "
            + "(:desde IS NULL OR b.fechaHora >= :desde) AND "
            + "(:hasta IS NULL OR b.fechaHora <= :hasta) "
            + "ORDER BY b.fechaHora DESC")
    Page<BitacoraAuditoria> buscarConFiltros(
            @Param("usuarioId") Long usuarioId,
            @Param("modulo") String modulo,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta,
            Pageable pageable);
}
