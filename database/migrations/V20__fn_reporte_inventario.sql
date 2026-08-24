-- ============================================================================
-- fn_reporte_inventario
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE.
--
-- Propósito: reporte de inventario y disponibilidad de libros.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_reporte_inventario(
    p_categoria_id INTEGER DEFAULT NULL,
    p_estado_stock TEXT DEFAULT NULL,
    p_busqueda TEXT DEFAULT NULL
)
RETURNS TABLE (
    libro_id            BIGINT,
    titulo              VARCHAR(255),
    isbn                VARCHAR(13),
    autor_nombre        TEXT,
    categoria_nombre    TEXT,
    stock_total         SMALLINT,
    stock_disponible    SMALLINT,
    estado_disponibilidad TEXT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        l.id AS libro_id,
        l.titulo,
        l.isbn,
        COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor') AS autor_nombre,
        COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoría') AS categoria_nombre,
        l.stock_total,
        l.stock_disponible,
        CASE
            WHEN l.stock_disponible = 0 THEN 'Agotado'
            WHEN l.stock_disponible = 1 THEN 'Baja disponibilidad'
            ELSE 'Disponible'
        END AS estado_disponibilidad
    FROM libros l
    LEFT JOIN libro_autores la ON la.libro_id = l.id
    LEFT JOIN autores a ON a.id = la.autor_id
    LEFT JOIN libro_categorias lc ON lc.libro_id = l.id
    LEFT JOIN categorias c ON c.id = lc.categoria_id
    WHERE (p_categoria_id IS NULL OR lc.categoria_id = p_categoria_id)
      AND (p_busqueda IS NULL OR l.titulo ILIKE '%' || p_busqueda || '%' OR l.isbn ILIKE '%' || p_busqueda || '%'
           OR a.nombre ILIKE '%' || p_busqueda || '%')
    GROUP BY l.id, l.titulo, l.isbn, l.stock_total, l.stock_disponible
    HAVING (p_estado_stock IS NULL
            OR (p_estado_stock = 'agotado' AND l.stock_disponible = 0)
            OR (p_estado_stock = 'baja' AND l.stock_disponible = 1 AND l.stock_disponible > 0)
            OR (p_estado_stock = 'disponible' AND l.stock_disponible > 1))
    ORDER BY l.stock_disponible ASC, l.titulo ASC;
$$;
