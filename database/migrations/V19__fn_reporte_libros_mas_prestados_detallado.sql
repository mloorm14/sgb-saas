-- ============================================================================
-- fn_reporte_libros_mas_prestados_detallado
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE.
--
-- Propósito: reemplaza fn_reporte_libros_mas_prestados con información
-- extendida para decisiones de compra: autor, categoría y porcentaje
-- del total de préstamos.
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_reporte_libros_mas_prestados_detallado(
    p_limite INTEGER DEFAULT 10,
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL,
    p_categoria_id INTEGER DEFAULT NULL
)
RETURNS TABLE (
    libro_id         BIGINT,
    titulo           VARCHAR(255),
    isbn             VARCHAR(13),
    autor_nombre     TEXT,
    categoria_nombre TEXT,
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
    prestamos_por_libro AS (
        SELECT
            pr.libro_id,
            COUNT(*) AS total_prestamos
        FROM prestamos pr
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
        GROUP BY pr.libro_id
    ),
    libros_con_categoria AS (
        SELECT
            ppl.libro_id,
            ppl.total_prestamos,
            string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre) AS categoria_nombre
        FROM prestamos_por_libro ppl
        JOIN libros l ON l.id = ppl.libro_id
        LEFT JOIN libro_categorias lc ON lc.libro_id = l.id
        LEFT JOIN categorias c ON c.id = lc.categoria_id
        WHERE (p_categoria_id IS NULL OR lc.categoria_id = p_categoria_id)
        GROUP BY ppl.libro_id, ppl.total_prestamos
    )
    SELECT
        l.id AS libro_id,
        l.titulo,
        l.isbn,
        COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor') AS autor_nombre,
        COALESCE(lc2.categoria_nombre, 'Sin categoría') AS categoria_nombre,
        lc2.total_prestamos,
        ROUND(lc2.total_prestamos * 100.0 / NULLIF(tg.total, 0), 1) AS porcentaje
    FROM libros_con_categoria lc2
    JOIN libros l ON l.id = lc2.libro_id
    LEFT JOIN libro_autores la ON la.libro_id = l.id
    LEFT JOIN autores a ON a.id = la.autor_id
    CROSS JOIN total_general tg
    GROUP BY l.id, l.titulo, l.isbn, lc2.total_prestamos, lc2.categoria_nombre, tg.total
    ORDER BY lc2.total_prestamos DESC
    LIMIT p_limite;
$$;
