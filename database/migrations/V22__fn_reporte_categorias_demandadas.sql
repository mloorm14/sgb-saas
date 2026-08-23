-- ============================================================================
-- fn_reporte_categorias_demandadas
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE.
--
-- Propósito: reporte de categorías más demandadas por número de préstamos.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_reporte_categorias_demandadas(
    p_limite INTEGER DEFAULT 10,
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    categoria_id    INTEGER,
    categoria_nombre VARCHAR(80),
    total_prestamos  BIGINT,
    porcentaje       NUMERIC
)
LANGUAGE sql
STABLE
AS $$
    WITH total_general AS (
        SELECT COUNT(*) AS total
        FROM prestamos pr
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
    ),
    prestamos_por_categoria AS (
        SELECT
            c.id AS categoria_id,
            c.nombre AS categoria_nombre,
            COUNT(*) AS total_prestamos
        FROM prestamos pr
        JOIN libros l ON l.id = pr.libro_id
        JOIN libro_categorias lc ON lc.libro_id = l.id
        JOIN categorias c ON c.id = lc.categoria_id
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
        GROUP BY c.id, c.nombre
    )
    SELECT
        ppc.categoria_id,
        ppc.categoria_nombre,
        ppc.total_prestamos,
        ROUND(ppc.total_prestamos * 100.0 / NULLIF(tg.total, 0), 1) AS porcentaje
    FROM prestamos_por_categoria ppc
    CROSS JOIN total_general tg
    ORDER BY ppc.total_prestamos DESC
    LIMIT p_limite;
$$;
