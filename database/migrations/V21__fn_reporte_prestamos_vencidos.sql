-- ============================================================================
-- fn_reporte_prestamos_vencidos
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE.
--
-- Propósito: reporte de préstamos vencidos activos (no devueltos y con
-- fecha de devolución estimada vencida).
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_reporte_prestamos_vencidos(
    p_dias_atraso_min INTEGER DEFAULT NULL,
    p_busqueda TEXT DEFAULT NULL
)
RETURNS TABLE (
    prestamo_id             BIGINT,
    usuario_nombre          TEXT,
    usuario_correo          VARCHAR(255),
    libro_titulo            VARCHAR(255),
    libro_isbn              VARCHAR(13),
    fecha_devolucion_estimada TIMESTAMPTZ,
    dias_atraso             BIGINT,
    monto_multa_estimada    NUMERIC
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        p.id AS prestamo_id,
        u.nombre || ' ' || u.apellido AS usuario_nombre,
        u.correo AS usuario_correo,
        l.titulo AS libro_titulo,
        l.isbn AS libro_isbn,
        p.fecha_devolucion_estimada,
        EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada)::BIGINT AS dias_atraso,
        ROUND(
            EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada)::NUMERIC
            * COALESCE(
                (SELECT cs.valor_numerico FROM configuracion_sistema cs WHERE cs.clave = 'multa_por_dia_atraso'),
                0.50
            ), 2
        ) AS monto_multa_estimada
    FROM prestamos p
    JOIN usuarios u ON u.id = p.usuario_id
    JOIN libros l ON l.id = p.libro_id
    JOIN estados_prestamo ep ON ep.id = p.estado_prestamo_id
    WHERE ep.nombre IN ('ACTIVO', 'RENOVADO')
      AND p.fecha_devolucion_estimada < NOW()
      AND p.fecha_devolucion_real IS NULL
      AND (p_dias_atraso_min IS NULL
           OR EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) >= p_dias_atraso_min)
      AND (p_busqueda IS NULL
           OR u.nombre ILIKE '%' || p_busqueda || '%'
           OR u.correo ILIKE '%' || p_busqueda || '%'
           OR l.titulo ILIKE '%' || p_busqueda || '%')
    ORDER BY dias_atraso DESC;
$$;
