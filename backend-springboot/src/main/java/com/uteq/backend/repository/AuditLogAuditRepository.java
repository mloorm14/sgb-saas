package com.uteq.backend.repository;

import com.uteq.backend.entity.AuditLogAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CRUD de {@code bitacora_auditoria} más filtro paginado (query nativa con casts para parámetros NULL).
 */
@Repository
public interface AuditLogAuditRepository extends JpaRepository<AuditLogAudit, Long> {

    // Sin ORDER BY interno: el orden lo inyecta Spring Data desde el
    // Pageable del controller (@PageableDefault sort="fecha_hora"), que en
    // queries nativas pasa el nombre de columna TAL CUAL -- por eso el sort
    // debe usar el nombre fisico (fecha_hora), no la propiedad (fechaHora).
    @Query(value = "SELECT * FROM bitacora_auditoria b WHERE "
            + "(CAST(:userId AS bigint) IS NULL OR b.usuario_id = CAST(:userId AS bigint)) AND "
            + "(CAST(:module AS text) IS NULL OR b.tabla_afectada = CAST(:module AS text)) AND "
            + "(CAST(:from AS timestamptz) IS NULL OR b.fecha_hora >= CAST(:from AS timestamptz)) AND "
            + "(CAST(:until AS timestamptz) IS NULL OR b.fecha_hora <= CAST(:until AS timestamptz))",
           countQuery = "SELECT count(*) FROM bitacora_auditoria b WHERE "
            + "(CAST(:userId AS bigint) IS NULL OR b.usuario_id = CAST(:userId AS bigint)) AND "
            + "(CAST(:module AS text) IS NULL OR b.tabla_afectada = CAST(:module AS text)) AND "
            + "(CAST(:from AS timestamptz) IS NULL OR b.fecha_hora >= CAST(:from AS timestamptz)) AND "
            + "(CAST(:until AS timestamptz) IS NULL OR b.fecha_hora <= CAST(:until AS timestamptz))",
           nativeQuery = true)
    Page<AuditLogAudit> searchWithFilters(
            @Param("userId") Long userId,
            @Param("module") String module,
            @Param("from") OffsetDateTime from,
            @Param("until") OffsetDateTime until,
            Pageable pageable);

    // Resumen por categoría: una sola query de agregación en vez de 8
    // llamadas al listado paginado. Devuelve Object[] porque la query
    // nativa no mapea directamente a un record -- el service
    // transforma a ResumenCategoriaAuditoriaDTO.
    @Query(value = """
            SELECT b.tabla_afectada,
                   COUNT(*) AS total_eventos,
                   COUNT(*) FILTER (WHERE b.fecha_hora >= :fromToday) AS eventos_hoy,
                   MAX(b.fecha_hora) AS ultimo_evento
            FROM bitacora_auditoria b
            GROUP BY b.tabla_afectada
            ORDER BY b.tabla_afectada
            """, nativeQuery = true)
    List<Object[]> summaryByCategory(@Param("fromToday") OffsetDateTime fromToday);

    // Login fallidos en las últimas 24h (para decidir "Revisar" en sesiones)
    @Query(value = """
            SELECT COUNT(*) FROM bitacora_auditoria
            WHERE tabla_afectada = 'sesiones'
              AND tipo_operacion = 'LOGIN_FAIL'
              AND fecha_hora >= :from
            """, nativeQuery = true)
    long contarLoginFailRecientes(@Param("from") OffsetDateTime from);
}
