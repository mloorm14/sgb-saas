-- ============================================================================
-- fn_reporte_resumen_financiero_multas
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE (1 fila).
--
-- Propósito: reporte gerencial financiero (dashboard GERENTE/ADMIN, pedido
-- ampliado de Cajas) -- total recaudado en multas ya PAGADAs y total
-- pendiente de cobro en multas PENDIENTEs, en un mismo viaje. Mismo estilo
-- que fn_reporte_indice_morosidad (JOIN + agregación condicional, no CRUD
-- elemental de una sola tabla).
--
-- Nota de diseño: se filtra por nombre del catálogo (estados_multa.nombre),
-- no por el id crudo de estado_multa_id, mismo criterio que
-- fn_reporte_indice_morosidad -- evita depender de que los ids del seed
-- (1=PENDIENTE, 2=PAGADA, 3=ANULADA en V10__seed_catalogos_y_admin.sql) no
-- cambien nunca.
--
-- Nota de alcance: solo filtra por un único rango de fechas (aplicado sobre
-- fecha_generada, la única fecha que toda multa tiene siempre -- una multa
-- PENDIENTE no tiene fecha_pagada). No calcula "período actual vs período
-- anterior" (parte del wishlist original de Cajas): eso exigiría correr la
-- agregación dos veces con dos rangos y devolver deltas, una función
-- distinta y más compleja -- fuera de alcance de esta corrección puntual de
-- 2 días, queda como seguimiento explícito si se prioriza después.
--
-- Parámetros (nombrados):
--   p_desde TIMESTAMPTZ DEFAULT NULL -- filtro opcional, inclusive
--   p_hasta TIMESTAMPTZ DEFAULT NULL -- filtro opcional, inclusive
--
-- Retorno TABLE (1 fila):
--   total_recaudado   NUMERIC(12,2) -- SUM(monto) de multas PAGADAs
--   total_pendiente   NUMERIC(12,2) -- SUM(monto) de multas PENDIENTEs
--
-- Tablas afectadas: solo lectura (multas, estados_multa).
-- ============================================================================
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
