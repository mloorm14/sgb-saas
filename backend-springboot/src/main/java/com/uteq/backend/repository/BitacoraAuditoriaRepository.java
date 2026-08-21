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
 * opcionales e independientes entre sí (usuario, módulo, rango de fecha).
 *
 * Query NATIVA con casts explícitos de PostgreSQL: la versión JPQL con
 * "(:param IS NULL OR columna = :param)" falla con 500 cuando TODOS los
 * filtros vienen vacíos (caso real: abrir la pantalla sin filtrar) --
 * Hibernate envía los parámetros NULL sin tipo y PostgreSQL no puede
 * inferirlo para la comparación ("could not determine data type of
 * parameter"). Los CAST(:param AS tipo) fijan el tipo en el SQL y el
 * planificador resuelve siempre.
 */
@Repository
public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Long> {

    // Sin ORDER BY interno: el orden lo inyecta Spring Data desde el
    // Pageable del controller (@PageableDefault sort="fecha_hora"), que en
    // queries nativas pasa el nombre de columna TAL CUAL -- por eso el sort
    // debe usar el nombre fisico (fecha_hora), no la propiedad (fechaHora).
    @Query(value = "SELECT * FROM bitacora_auditoria b WHERE "
            + "(CAST(:usuarioId AS bigint) IS NULL OR b.usuario_id = CAST(:usuarioId AS bigint)) AND "
            + "(CAST(:modulo AS text) IS NULL OR b.tabla_afectada = CAST(:modulo AS text)) AND "
            + "(CAST(:desde AS timestamptz) IS NULL OR b.fecha_hora >= CAST(:desde AS timestamptz)) AND "
            + "(CAST(:hasta AS timestamptz) IS NULL OR b.fecha_hora <= CAST(:hasta AS timestamptz))",
           countQuery = "SELECT count(*) FROM bitacora_auditoria b WHERE "
            + "(CAST(:usuarioId AS bigint) IS NULL OR b.usuario_id = CAST(:usuarioId AS bigint)) AND "
            + "(CAST(:modulo AS text) IS NULL OR b.tabla_afectada = CAST(:modulo AS text)) AND "
            + "(CAST(:desde AS timestamptz) IS NULL OR b.fecha_hora >= CAST(:desde AS timestamptz)) AND "
            + "(CAST(:hasta AS timestamptz) IS NULL OR b.fecha_hora <= CAST(:hasta AS timestamptz))",
           nativeQuery = true)
    Page<BitacoraAuditoria> buscarConFiltros(
            @Param("usuarioId") Long usuarioId,
            @Param("modulo") String modulo,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta,
            Pageable pageable);
}
