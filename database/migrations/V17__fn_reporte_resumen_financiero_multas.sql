-- fn_reporte_resumen_financiero_multas
-- Función que faltaba en la BD: total recaudado (multas PAGADAs) y total
-- pendiente de cobro (multas PENDIENTEs). Usada por GET
-- /api/v1/multas/reportes/resumen-financiero (dashboard GERENTE/ADMIN).
CREATE OR REPLACE FUNCTION fn_reporte_resumen_financiero_multas(
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    total_recaudado NUMERIC(12,2),
    total_pendiente NUMERIC(12,2)
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        COALESCE(SUM(m.monto) FILTER (WHERE em.nombre = 'PAGADA'), 0)::NUMERIC(12,2)
            AS total_recaudado,
        COALESCE(SUM(m.monto) FILTER (WHERE em.nombre = 'PENDIENTE'), 0)::NUMERIC(12,2)
            AS total_pendiente
    FROM multas m
    JOIN estados_multa em ON em.id = m.estado_multa_id
    WHERE (p_desde IS NULL OR m.fecha_generada >= p_desde)
      AND (p_hasta IS NULL OR m.fecha_generada <= p_hasta);
$$;
