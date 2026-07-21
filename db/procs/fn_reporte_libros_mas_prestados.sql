-- ============================================================================
-- fn_reporte_libros_mas_prestados
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE (varias filas).
--
-- Propósito: reporte de los libros con más préstamos, opcionalmente acotado
-- a un rango de fechas. Requiere JOIN + agregación (GROUP BY/COUNT) — no es
-- CRUD elemental.
--
-- Parámetros (nombrados):
--   p_limite INTEGER    DEFAULT 10   — máximo de filas a retornar
--   p_desde  TIMESTAMPTZ DEFAULT NULL — filtra prestamos.fecha_prestamo >= p_desde (NULL = sin límite inferior)
--   p_hasta  TIMESTAMPTZ DEFAULT NULL — filtra prestamos.fecha_prestamo <= p_hasta (NULL = sin límite superior)
--
-- Retorno TABLE:
--   libro_id         BIGINT
--   titulo           VARCHAR(255)
--   isbn             VARCHAR(13)
--   total_prestamos  BIGINT
--
-- Tablas afectadas: solo lectura (prestamos, libros).
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_reporte_libros_mas_prestados(
    p_limite INTEGER DEFAULT 10,
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    libro_id         BIGINT,
    titulo           VARCHAR(255),
    isbn             VARCHAR(13),
    total_prestamos  BIGINT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        l.id,
        l.titulo,
        l.isbn,
        COUNT(*) AS total_prestamos
    FROM prestamos p
    JOIN libros l ON l.id = p.libro_id
    WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
      AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
    GROUP BY l.id, l.titulo, l.isbn
    ORDER BY total_prestamos DESC
    LIMIT p_limite;
$$;
