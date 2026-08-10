-- ============================================================================
-- fn_reporte_indice_morosidad
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE (varias filas).
--
-- Propósito: reporte gerencial de morosidad (Módulo 7 del roadmap, RF-12) --
-- usuarios con multas PENDIENTES, el monto total que adeudan y los días de
-- atraso promedio del/los préstamo(s) que originaron esas multas. Mismo
-- estilo que fn_reporte_libros_mas_prestados (JOIN + agregación, no CRUD
-- elemental).
--
-- Nota de diseño: "días de atraso" no es una columna de `multas` (la tabla
-- no la tiene, ver db/schema.sql) -- se deriva de `prestamos` vía
-- prestamo_id: si el préstamo YA fue devuelto, el atraso es
-- fecha_devolucion_real - fecha_devolucion_estimada; si todavía está
-- pendiente de devolución, se usa NOW() en su lugar (atraso "corriendo").
-- GREATEST(0, ...) evita días negativos para el caso borde de una multa por
-- daño (no por atraso) sobre un préstamo devuelto a tiempo.
--
-- Parámetros (nombrados):
--   p_limite INTEGER DEFAULT 10 — máximo de filas a retornar
--
-- Retorno TABLE:
--   usuario_id                  BIGINT
--   nombre                      VARCHAR(100)
--   apellido                    VARCHAR(100)
--   correo                      VARCHAR(150)
--   monto_total_adeudado        NUMERIC(10,2)
--   cantidad_multas_pendientes  BIGINT
--   dias_atraso_promedio        NUMERIC
--
-- Tablas afectadas: solo lectura (multas, estados_multa, prestamos, usuarios).
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_reporte_indice_morosidad(
    p_limite INTEGER DEFAULT 10
)
RETURNS TABLE (
    usuario_id                  BIGINT,
    nombre                       VARCHAR(100),
    apellido                     VARCHAR(100),
    correo                       VARCHAR(150),
    monto_total_adeudado         NUMERIC(10,2),
    cantidad_multas_pendientes   BIGINT,
    dias_atraso_promedio         NUMERIC
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        u.id,
        u.nombre,
        u.apellido,
        u.correo,
        SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
        COUNT(m.id) AS cantidad_multas_pendientes,
        ROUND(
            AVG(
                GREATEST(
                    0,
                    EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada))
                )
            )::NUMERIC,
            1
        ) AS dias_atraso_promedio
    FROM multas m
    JOIN estados_multa em ON em.id = m.estado_multa_id
    JOIN prestamos p ON p.id = m.prestamo_id
    JOIN usuarios u ON u.id = p.usuario_id
    WHERE em.nombre = 'PENDIENTE'
    GROUP BY u.id, u.nombre, u.apellido, u.correo
    ORDER BY monto_total_adeudado DESC
    LIMIT p_limite;
$$;
