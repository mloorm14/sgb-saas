-- ============================================================================
-- fn_reporte_uso_por_periodo
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE (varias filas).
--
-- Propósito: reporte gerencial de uso (Módulo 7 del roadmap, RF-12) --
-- préstamos y devoluciones agrupados por día/semana/mes, para el gráfico de
-- líneas del dashboard.
--
-- Nota de diseño: p_granularidad se recibe en español ('dia'/'semana'/'mes',
-- mismos valores que usaría el <select> del dashboard en el frontend) y se
-- traduce internamente al nombre de campo que espera date_trunc()
-- ('day'/'week'/'month'); cualquier valor no reconocido cae a 'day' por
-- seguridad (el backend igual valida contra una lista blanca antes de
-- llamar a esta función -- ver PrestamoService.reporteUsoPorPeriodo -- así
-- que este fallback es solo defensa en profundidad, no la única validación).
--
-- Préstamos y devoluciones se agregan por separado (dos CTEs) y se combinan
-- con FULL OUTER JOIN por período: un período puede tener préstamos sin
-- devoluciones todavía, o (menos común) devoluciones de préstamos iniciados
-- en un período anterior sin préstamos nuevos ese día.
--
-- Parámetros (nombrados):
--   p_granularidad TEXT        DEFAULT 'dia'  — 'dia' | 'semana' | 'mes'
--   p_desde        TIMESTAMPTZ DEFAULT NULL   — límite inferior (NULL = sin límite)
--   p_hasta        TIMESTAMPTZ DEFAULT NULL   — límite superior (NULL = sin límite)
--
-- Retorno TABLE:
--   periodo             TIMESTAMPTZ  -- inicio del período (date_trunc)
--   total_prestamos      BIGINT
--   total_devoluciones    BIGINT
--
-- Tablas afectadas: solo lectura (prestamos).
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_reporte_uso_por_periodo(
    p_granularidad TEXT DEFAULT 'dia',
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    periodo             TIMESTAMPTZ,
    total_prestamos      BIGINT,
    total_devoluciones    BIGINT
)
LANGUAGE sql
STABLE
AS $$
    WITH campo AS (
        SELECT CASE p_granularidad
                   WHEN 'semana' THEN 'week'
                   WHEN 'mes'    THEN 'month'
                   ELSE 'day'
               END AS valor
    ),
    prestamos_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_prestamo) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
        GROUP BY 1
    ),
    devoluciones_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_devolucion_real) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE p.fecha_devolucion_real IS NOT NULL
          AND (p_desde IS NULL OR p.fecha_devolucion_real >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_devolucion_real <= p_hasta)
        GROUP BY 1
    )
    SELECT
        COALESCE(pa.periodo, da.periodo) AS periodo,
        COALESCE(pa.total, 0) AS total_prestamos,
        COALESCE(da.total, 0) AS total_devoluciones
    FROM prestamos_agg pa
    FULL OUTER JOIN devoluciones_agg da ON da.periodo = pa.periodo
    ORDER BY periodo;
$$;
